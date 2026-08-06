package com.weave.recommend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.weave.recommend.mapper.UserActionMapper;
import com.weave.recommend.model.dto.PostCoOccurrenceDto;
import com.weave.recommend.model.dto.PostTotalWeightDto;
import com.weave.recommend.model.dto.PostWeightDto;
import com.weave.recommend.service.RecommendService;
import com.weave.redis.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.weave.redis.constant.CacheKey;
import com.weave.recommend.model.dto.SimilarPostDto;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐服务实现类
 * 基于物品的协同过滤推荐算法（加权版）
 * 核心思想：给用户推荐与他历史交互帖子相似的其他帖子
 * 权重比例：浏览:点赞:收藏 = 1:3:5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements RecommendService {

    private final UserActionMapper userActionMapper;

    private final RedisTemplate<String, Object> redisTemplate;

    private final RedisUtil redisUtil;

    /** Redis中存储帖子相似度的key前缀 */
    private static final String SIMILAR_POST_KEY = "similar:post";
    /** 获取用户最近交互记录的数量 */
    private static final int RECENT_POST_LIMIT = 5;
    /** 每篇帖子保留的最相似帖子数量 */
    private static final int TOP_SIMILAR_POSTS = 20;
    /** 计算相似度时考虑的天数（近30天） */
    private static final int DAYS_TO_CONSIDER = 30;

    /**
     * 为用户生成个性化推荐（加权版）
     * @param userId 用户ID
     * @param limit 推荐数量限制
     * @return 推荐的帖子ID列表
     */
    @Override
    public List<Long> recommend(Long userId, int limit) {
        // 0. 参数校验
        if (userId == null) {
            return getHotPostIds(limit);
        }
        // 1. 获取用户最近的交互帖子（带权重，SQL聚合）
        Map<Long, Double> recentInteractions = getRecentUserInteractions(userId);

        // 2. 如果用户没有交互记录，返回热门帖子ID（冷启动处理）
        if (recentInteractions.isEmpty()) {
            return getHotPostIds(limit);
        }

        // 3. 候选帖子评分映射
        Map<Long, Double> candidateScores = new HashMap<>();

        // 4. 遍历用户最近的每篇交互帖子，按用户对该帖子的兴趣权重加权推荐
        for (Map.Entry<Long, Double> entry : recentInteractions.entrySet()) {
            Long postId = entry.getKey();
            double userWeight = entry.getValue();
            List<SimilarPostDto> similarPosts = getSimilarPostsFromRedis(postId);
            // 5. 合并相似帖子的评分：用户权重 × 相似度
            for (SimilarPostDto similar : similarPosts) {
                // 排除用户已交互的帖子
                if (!recentInteractions.containsKey(similar.getPostId())) {
                    candidateScores.merge(similar.getPostId(),
                            userWeight * similar.getScore(), Double::sum);
                }
            }
        }

        // 6. 按评分降序排序，取前N个推荐
        List<Long> recommendPostIds = candidateScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 7. 如果没有推荐结果，返回热门帖子ID
        if (recommendPostIds.isEmpty()) {
            return getHotPostIds(limit);
        }

        // 8. 返回推荐帖子ID列表
        return recommendPostIds;
    }

    /**
     * 计算帖子相似度（离线计算，定时任务调用）
     * 使用加权基于物品的协同过滤算法
     * 算法步骤：
     * 1. SQL自连接计算帖子共现对及共现权重
     * 2. 聚合每篇帖子的共现总权重
     * 3. 使用余弦相似度计算帖子相似度
     * 4. 保存相似度结果到Redis
     */
    @Override
    public void computePostSimilarity() {
        log.info("开始计算帖子相似度（加权）...");
        LocalDateTime since = LocalDateTime.now().minusDays(DAYS_TO_CONSIDER);

        // 1. 获取帖子共现对及其权重（SQL自连接）
        List<PostCoOccurrenceDto> coOccurrencePairs = userActionMapper.selectPostCoOccurrencePairs(since);

        // 2. 聚合共现权重：同一对帖子的共现权重累加
        Map<Long, Map<Long, Double>> coOccurrenceMatrix = new HashMap<>();
        for (PostCoOccurrenceDto row : coOccurrencePairs) {
            coOccurrenceMatrix.computeIfAbsent(row.getPostA(), k -> new HashMap<>())
                    .merge(row.getPostB(), row.getCoWeight(), Double::sum);
            coOccurrenceMatrix.computeIfAbsent(row.getPostB(), k -> new HashMap<>())
                    .merge(row.getPostA(), row.getCoWeight(), Double::sum);
        }

        // 3. 获取每篇帖子的总权重（分母）
        List<PostTotalWeightDto> totalWeights = userActionMapper.selectPostTotalWeights(since);
        Map<Long, Double> postWeightSum = new HashMap<>();
        for (PostTotalWeightDto row : totalWeights) {
            postWeightSum.put(row.getPostId(), row.getTotalWeight());
        }

        // 4. 计算每篇帖子的相似帖子列表
        for (Map.Entry<Long, Map<Long, Double>> entry : coOccurrenceMatrix.entrySet()) {
            Long postId = entry.getKey();
            Map<Long, Double> coOccurrences = entry.getValue();
            List<SimilarPostDto> similarPosts = new ArrayList<>();

            // 余弦相似度 = 共现权重 / sqrt(帖子A总权重 * 帖子B总权重)
            for (Map.Entry<Long, Double> coEntry : coOccurrences.entrySet()) {
                Long similarId = coEntry.getKey();
                double coWeight = coEntry.getValue();
                double weightA = postWeightSum.getOrDefault(postId, 1.0);
                double weightB = postWeightSum.getOrDefault(similarId, 1.0);
                double similarity = coWeight / Math.sqrt(weightA * weightB);
                similarPosts.add(SimilarPostDto.builder()
                        .postId(similarId)
                        .score(similarity)
                        .build());
            }

            // 按相似度降序排序，保留Top N
            similarPosts.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            List<SimilarPostDto> topSimilar = similarPosts.stream()
                    .limit(TOP_SIMILAR_POSTS)
                    .collect(Collectors.toList());

            // 保存到Redis
            saveSimilarPostsToRedis(postId, topSimilar);
        }

        log.info("帖子相似度计算完成（加权）！共计算 {} 篇帖子的相似帖子", coOccurrenceMatrix.size());
    }

    /**
     * 从Redis中获取帖子的相似帖子列表
     */
    private List<SimilarPostDto> getSimilarPostsFromRedis(Long postId) {
        String key = CacheKey.buildCacheKey(SIMILAR_POST_KEY, postId);
        List<SimilarPostDto> data = redisUtil.get(key, new TypeReference<>() {});
        if (data != null) {
            return data;
        }
        return Collections.emptyList();
    }

    /**
     * 将帖子相似度列表保存到Redis
     */
    private void saveSimilarPostsToRedis(Long postId, List<SimilarPostDto> similarPosts) {
        String key = CacheKey.buildCacheKey(SIMILAR_POST_KEY, postId);
        redisUtil.set(key, similarPosts);
    }

    /**
     * 获取热门帖子ID列表（冷启动 fallback，按热度权重）
     */
    private List<Long> getHotPostIds(int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<PostWeightDto> hotPosts = userActionMapper.selectHotPosts(since, limit);
        return hotPosts.stream()
                .map(PostWeightDto::getPostId)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户最近的交互帖子及加权权重
     */
    private Map<Long, Double> getRecentUserInteractions(Long userId) {
        List<PostWeightDto> rows = userActionMapper.selectRecentUserInteractions(userId, RECENT_POST_LIMIT);
        Map<Long, Double> result = new HashMap<>();
        for (PostWeightDto row : rows) {
            result.put(row.getPostId(), row.getWeightSum().doubleValue());
        }
        return result;
    }
}
