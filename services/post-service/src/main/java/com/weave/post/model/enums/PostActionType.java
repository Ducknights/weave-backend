package com.weave.post.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.weave.redis.constant.CacheKey;
import com.weave.model.constant.PostOperation;

/**
 * 帖子操作类型枚举 - 封装所有帖子操作的配置信息
 */
@Getter
@AllArgsConstructor
public enum PostActionType {

    VIEW(PostOperation.VIEW_COUNT, PostOperation.VIEW, CacheKey.USER_VIEWED_POSTS),
    
    LIKE(PostOperation.LIKE_COUNT, PostOperation.LIKE, CacheKey.USER_LIKED_POSTS),
    UNLIKE(PostOperation.LIKE_COUNT, PostOperation.UNLIKE, CacheKey.USER_LIKED_POSTS),

    COLLECT(PostOperation.COLLECT_COUNT, PostOperation.COLLECT, CacheKey.USER_COLLECTED_POSTS),
    UNCOLLECT(PostOperation.COLLECT_COUNT, PostOperation.UNCOLLECT, CacheKey.USER_COLLECTED_POSTS);

    // 帖子缓存前缀
    private final String cacheField;
    // 操作
    private final String operation;
    // 用户缓存前缀
    private final String userCacheKeyPrefix;

    public boolean isIncrement() {
        return !name().startsWith("UN"); 
    }
}