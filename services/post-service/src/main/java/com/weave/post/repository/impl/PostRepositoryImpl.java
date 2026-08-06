package com.weave.post.repository.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.weave.redis.constant.CacheKey;
import com.weave.rabbitmq.constant.MQueue;
import com.weave.post.mapper.PostMapper;
import com.weave.post.mapper.PostResourceMapper;
import com.weave.post.model.constant.CacheSpec;
import com.weave.post.model.entity.Post;
import com.weave.post.model.entity.PostResource;
import com.weave.post.repository.PostRepository;
import com.weave.rabbitmq.util.MQUtil;
import com.weave.redis.util.RedisUtil;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Log4j2
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

    private final RedisUtil redisUtil;
    private final PostMapper postMapper;
    private final PostResourceMapper postResourceMapper;
    private final ObjectMapper objectMapper;
    private final MQUtil mqUtil;

    @Override
    public List<Post> getPostsFromCacheOrDb(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }

        // 先从缓存批量获取帖子
        Map<Long, Post> cachedPostsMap = getPostsFromCache(ids);
        Set<Long> cachedIds = cachedPostsMap.keySet();

        // 筛选出未命中缓存的 ID
        List<Long> needQueryIds = ids.stream()
                .filter(id -> !cachedIds.contains(id))
                .collect(Collectors.toList());

        Map<Long, Post> dbPostMap = new HashMap<>();
        if (!needQueryIds.isEmpty()) {
            // 从数据库查询
            List<Post> dbPosts = postMapper.selectPublishedPostByIds(needQueryIds);
            // 记录未找到的帖子ID
            Set<Long> notFoundIds = new HashSet<>(needQueryIds);
            // 构建映射并移除已找到的ID
            for (Post post : dbPosts) {
                notFoundIds.remove(post.getPostId());
                dbPostMap.put(post.getPostId(), post);
            }
            // 批量查询帖子资源
            fillPostResources(dbPosts);
            List<Post> postsForCache = new ArrayList<>(dbPosts);
            // 剩余未找到的id构建空对象
            for (Long id : notFoundIds) {
                Post emptyPost = Post.buildEmpty(id);
                postsForCache.add(emptyPost);
            }
            // 异步写入缓存
            mqUtil.cachePost(postsForCache);
        }

        // 合并结果：按 ids 顺序，优先取缓存，缓存未命中取 DB
        List<Post> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Post post = cachedPostsMap.get(id);
            if (post == null) {
                post = dbPostMap.get(id);
            }
            result.add(post);
        }
        return result;
    }

    /**
     * 批量填充帖子资源
     */
    private void fillPostResources(List<Post> posts) {
        if (CollectionUtils.isEmpty(posts)) {
            return;
        }
        List<Long> postIds = posts.stream()
                .map(Post::getPostId)
                .toList();
        List<PostResource> resources = postResourceMapper.selectByPostIds(postIds);
        Map<Long, List<String>> resourceMap = resources.stream()
                .collect(Collectors.groupingBy(
                        PostResource::getPostId,
                        Collectors.mapping(PostResource::getResourcePath, Collectors.toList())));
        posts.forEach(post -> post.setResources(resourceMap.getOrDefault(post.getPostId(), List.of())));
    }

    /**
     * 从缓存批量获取帖子
     */
    private Map<Long, Post> getPostsFromCache(List<Long> postIds) {
        Map<Long, Post> result = new HashMap<>();
        for (Long postId : postIds) {
            String key = CacheKey.buildCacheKey(CacheSpec.PostHash.POST_DETAIL, postId);
            if (Boolean.TRUE.equals(redisUtil.hasKey(key))) {
                log.info("从缓存获取帖子：{}", postId);
                Map<Object, Object> hash = redisUtil.getForHash(key);
                // 手动处理 resources：Hash 中存的是逗号分隔 String，取出后转为 List
                Object resourcesRaw = hash.get("resources");
                hash.remove("resources");
                Post post = objectMapper.convertValue(hash, Post.class);
                if (resourcesRaw instanceof String s && !s.isEmpty()) {
                    post.setResources(Arrays.asList(s.split(",")));
                }
                result.put(postId, post);
            }
        }
        return result;
    }

    /**
     * 批量缓存帖子信息（Hash结构，resources 转为逗号分隔字符串存储）
     */
    @RabbitListener(queues = MQueue.POST_CACHE_QUEUE)
    public void cachePosts(List<Post> posts) {
        for (Post post : posts) {
            String cacheHashKey = CacheKey.buildCacheKey(CacheSpec.PostHash.POST_DETAIL, post.getPostId());
            Map<String, Object> postMap = objectMapper.convertValue(post, new TypeReference<>() {});
            // resources 从 List 转为逗号分隔 String，适配 Redis Hash 只支持标量
            List<String> resources = post.getResources();
            postMap.put("resources", resources != null && !resources.isEmpty() ? String.join(",", resources) : "");
            log.info("缓存帖子：{}", post);
            redisUtil.setForHash(cacheHashKey, postMap, CacheSpec.PostHash.TTL);
        }
    }
}
