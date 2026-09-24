package com.weave.post.repository.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.weave.model.util.CacheKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
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
        List<Long> needQueryIds = ids.stream().filter(id -> !cachedIds.contains(id)).collect(Collectors.toList());

        Map<Long, Post> dbPostMap = new LinkedHashMap<>();
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
                dbPostMap.put(id, emptyPost);
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
        // 构建缓存 key 列表
        List<String> keys = postIds.stream()
                .map(id -> CacheKeyUtil.buildCacheKey(CacheSpec.PostHash.POST_DETAIL, id))
                .collect(Collectors.toList());
        // 从缓存获取帖子
        Map<String, Post> cachePostsMap = redisUtil.getForRedisJson(keys, new TypeReference<>() {});
        Map<Long, Post> result = new LinkedHashMap<>();
        for (Map.Entry<String, Post> entry : cachePostsMap.entrySet()) {
            String key = entry.getKey();
            Post post = entry.getValue();
            // 拆解 key 获取帖子 ID
            Long id = Long.valueOf(CacheKeyUtil.demolishCacheKey(key, CacheSpec.PostHash.POST_DETAIL));
            result.put(id, post);
        }
        return result;
    }

    /**
     * 批量缓存帖子信息（RedisJson）
     */
    @RabbitListener(queues = MQueue.POST_CACHE_QUEUE)
    public void cachePosts(List<Post> posts) {
        if (CollectionUtils.isEmpty(posts)) {
            return;
        }
        Map<String, Post> postMap = posts.stream()
                .collect(Collectors.toMap(
                        post -> CacheKeyUtil.buildCacheKey(CacheSpec.PostHash.POST_DETAIL, post.getPostId()),
                        post -> post,
                        (existing, replacement) -> existing));
        redisUtil.setForRedisJson(postMap, CacheSpec.PostHash.TTL);
    }
}
