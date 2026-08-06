package com.weave.post.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weave.post.exception.BusinessException;
import com.weave.post.mapper.PostMapper;
import com.weave.post.model.enums.PostApiStatus;
import com.weave.post.model.enums.PostStatus;
import com.weave.post.repository.PostRepository;
import com.weave.redis.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.weave.redis.constant.CacheKey;
import com.weave.model.model.dto.ClubBriefDto;
import com.weave.model.model.dto.PostDetailVo;
import com.weave.model.model.dto.UserBriefDto;
import com.weave.post.feign.ClubFeignClient;
import com.weave.post.feign.RecommendFeignClient;
import com.weave.post.feign.UserFeignClient;
import com.weave.post.model.entity.Post;
import com.weave.post.service.PostCommandService;
import com.weave.post.service.PostQueryService;
import com.weave.security.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

@Service
@Log4j2
@RequiredArgsConstructor
public class PostQueryServiceImpl extends ServiceImpl<PostMapper, Post> implements PostQueryService {

    private final PostMapper postMapper;
    private final UserFeignClient userFeignClient;
    private final ClubFeignClient clubFeignClient;
    private final RecommendFeignClient recommendFeignClient;
    private final PostRepository postRepository;
    private final RedisUtil redisUtil;
    private final PostCommandService postCommandService;

    /**
     * 用户点击帖子详情
     */
    @Override
    public List<PostDetailVo> clickForDetails(Long id, Long userId) {
        // 从缓存或数据库获取帖子
        List<Post> posts = postRepository.getPostsFromCacheOrDb(List.of(id));
        if (posts.isEmpty() || posts.get(0) == null){
            throw new BusinessException(PostApiStatus.POST_NOT_FOUND);
        }
        Post post = posts.get(0);
        // 增加用户浏览记录
        postCommandService.addToHistory(userId, id);
        // 增加返回的帖子浏览数
        post.setViewCount(post.getViewCount() + 1);
        // 转换为 PostDetailVo
        return convertToPostDetailVoList(List.of(post));
    }

    /**
     * 获取推荐帖子
     */
    @Override
    public List<PostDetailVo> getRecommendPosts(Long userId, Integer limit) {
        // 调用推荐服务获取推荐帖子ID列表
        List<Long> recommendPostIds = recommendFeignClient.getRecommendations(userId, limit);
        if (CollectionUtils.isEmpty(recommendPostIds)){
            throw new BusinessException(PostApiStatus.POST_NOT_FOUND);
        }
        // 根据ID列表批量获取帖子
        return getPostsByIds(recommendPostIds);
    }

    /**
     * 获取用户隐藏的帖子
     */
    @Override
    public List<PostDetailVo> getHiddenPostsByUserId(Long userId) {
        // 从缓存或数据库获取帖子
        List<Post> posts = postMapper.selectHiddenPostByUserId(userId);
        // 转换为 PostDetailVo
        return convertToPostDetailVoList(posts);
    }

    @Override
    public List<PostDetailVo> getPostsByUser(Long userId) {
        List<Post> posts = postMapper.selectPostsByUser(userId);
        return convertToPostDetailVoList(posts);
    }

    /**
     * 根据ID列表批量获取帖子
     */
    @Override
    public List<PostDetailVo> getPostsByIds(List<Long> ids) {
        // 从缓存或数据库获取帖子
        List<Post> posts = postRepository.getPostsFromCacheOrDb(ids);
        // 转换为 PostDetailVo
        return convertToPostDetailVoList(posts);
    }

    /**
     * 获取最新帖子
     */
    @Override
    public List<PostDetailVo> getNewPosts(int page, int size) {
        // 1，查询帖子
        Page<Post> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getStatus, PostStatus.PUBLISHED)
                .orderByDesc(Post::getCreatedTime);
        Page<Post> postPage = postMapper.selectPage(pageParam, wrapper);
        if (postPage.getRecords().isEmpty()){ throw new BusinessException(PostApiStatus.POST_NOT_FOUND); }
        // 2，转换为 PostDetailVo
        return convertToPostDetailVoList(postPage.getRecords());
    }

    /**
     * 获取热门帖子
     */
    @Override
    public List<PostDetailVo> getHotPosts(int page, int size) {
        int limit = (page - 1) * size;
        if (limit <= 0) limit = 10;
        List<Long> hotPostIds = recommendFeignClient.getRecommendations(null, limit);
        if (CollectionUtils.isEmpty(hotPostIds)){
            throw new BusinessException(PostApiStatus.POST_NOT_FOUND);
        }
        // 根据ID列表批量获取帖子
        return getPostsByIds(hotPostIds);
    }

    /**
     * 将 Post 列表转换为 PostDetailVo 列表
     */
    private List<PostDetailVo> convertToPostDetailVoList(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }

        // 1，查询用户和社团信息
        Set<Long> userIds = new HashSet<>();
        Set<Long> clubIds = new HashSet<>();
        for (Post post : posts) {
            if (post.getUserId() != null) userIds.add(post.getUserId());
            if (post.getClubId() != null) clubIds.add(post.getClubId());
        }
        // 远程调用
        Map<Long, UserBriefDto> userMap = userIds.isEmpty() ? Collections.emptyMap() : userFeignClient.getUserInfosByIds(userIds);
        Map<Long, ClubBriefDto> clubMap = clubIds.isEmpty() ? Collections.emptyMap() : clubFeignClient.getClubInfosByIds(clubIds);

        // 获取当前用户 ID
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String cacheLikeKey;
        String cacheCollectKey;
        // 查询用户点赞和收藏的缓存信息
        if (currentUserId != null){
            cacheLikeKey = CacheKey.buildCacheKey(CacheKey.USER_LIKED_POSTS, currentUserId);
            cacheCollectKey = CacheKey.buildCacheKey(CacheKey.USER_COLLECTED_POSTS, currentUserId);
        } else {
            cacheCollectKey = null;
            cacheLikeKey = null;
        }
        // 转换为 PostDetailVo
        return posts.stream()
                .map(post -> {
                    UserBriefDto user = userMap.get(post.getUserId());
                    ClubBriefDto club = clubMap.get(post.getClubId());
                    // 查询用户点赞和收藏的缓存信息
                    boolean isLiked = getStatus(currentUserId,cacheLikeKey, post.getPostId());
                    boolean isCollected = getStatus(currentUserId,cacheCollectKey, post.getPostId());
                    // 构建返回的帖子详情VO
                    return PostDetailVo.builder()
                            .id(post.getPostId())
                            .userId(post.getUserId())
                            .username(user != null ? user.getName() : null)
                            .clubId(post.getClubId())
                            .clubName(club != null ? club.getName() : null)
                            .avatar(user != null ? user.getAvatar() : null)
                            .title(post.getTitle())
                            .content(post.getContent())
                            .likeCount(post.getLikeCount())
                            .likeStatus(isLiked)
                            .collectCount(post.getCollectCount())
                            .collectStatus(isCollected)
                            .commentCount(post.getCommentCount())
                            .viewCount(post.getViewCount())
                            .createdTime(post.getCreatedTime())
                            .updatedTime(post.getUpdatedTime())
                            .build();
                })
                .toList();
    }

    /**
     * 获取状态(点赞、收藏)
     */
    private boolean getStatus(Long currentUserId, String cacheKey, Long postId) {
        // 如果当前用户 ID 为空 (未登录)，则返回 false
        if (currentUserId == null) {
            return false;
        }
        // 1. 先尝试直接判断成员（命中则直接返回）
        Boolean isMember = redisUtil.isMember(cacheKey, postId);
        if (Boolean.TRUE.equals(isMember)) {
            return true;
        }
        // 2. 检查 key 是否存在，存在说明确实不是成员
        if (Boolean.TRUE.equals(redisUtil.hasKey(cacheKey))) {
            return false;
        }
        // 3. 加载缓存
        loadCache();
        // 4. 再次判断成员
        return Boolean.TRUE.equals(redisUtil.isMember(cacheKey, postId));
    }

    /**
     * 加载缓存（用户点赞和收藏的帖子）
     */
    public void loadCache() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        userFeignClient.loadCacheLikeAndCollect(currentUserId);
    }
}
