package com.weave.redis.util;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 布隆过滤器工具类。
 * <p>
 * 注意：帖子/用户 ID 是 Long，评论 ID 是字符串。三个过滤器各自的泛型不同，
 * 因此 ObjectProvider 的泛型参数必须分别匹配，不能统一用一个 <Long>。
 */
public class BloomUtil {

    private final ObjectProvider<RBloomFilter<Long>> userBloomFilterProvider;
    private final ObjectProvider<RBloomFilter<Long>> postBloomFilterProvider;
    private final ObjectProvider<RBloomFilter<String>> commentBloomFilterProvider;

    public BloomUtil(
            @Qualifier("userBloomFilter")
            ObjectProvider<RBloomFilter<Long>> userBloomFilterProvider,
            @Qualifier("postBloomFilter")
            ObjectProvider<RBloomFilter<Long>> postBloomFilterProvider,
            @Qualifier("commentBloomFilter")
            ObjectProvider<RBloomFilter<String>> commentBloomFilterProvider
    ) {
        this.userBloomFilterProvider = userBloomFilterProvider;
        this.postBloomFilterProvider = postBloomFilterProvider;
        this.commentBloomFilterProvider = commentBloomFilterProvider;
    }

    // —— 通用方法（泛型，过滤器未启用 / 目标为 null 时保守放行 true）——

    public <T> boolean contains(RBloomFilter<T> filter, T target) {
        if (filter == null || target == null) {
            return true;
        }
        return filter.contains(target);
    }

    public <T> boolean add(RBloomFilter<T> filter, T target) {
        if (filter == null || target == null) {
            return false;
        }
        return filter.add(target);
    }

    @SafeVarargs
    public final <T> boolean addAll(RBloomFilter<T> filter, T... targets) {
        if (filter == null || targets == null || targets.length == 0) {
            return false;
        }
        boolean anyAdded = false;
        for (T t : targets) {
            if (t != null && filter.add(t)) {
                anyAdded = true;
            }
        }
        return anyAdded;
    }

    public <T> boolean addAll(RBloomFilter<T> filter, Iterable<T> targets) {
        if (filter == null || targets == null) {
            return false;
        }
        boolean anyAdded = false;
        for (T t : targets) {
            if (t != null && filter.add(t)) {
                anyAdded = true;
            }
        }
        return anyAdded;
    }

    // —— 按资源类型的便捷方法 ——

    /** 用户ID (Long) 是否可能存在 */
    public boolean containsUser(Long userId) {
        return contains(userBloomFilterProvider.getIfAvailable(), userId);
    }

    /** 帖子ID (Long) 是否可能存在 */
    public boolean containsPost(Long postId) {
        return contains(postBloomFilterProvider.getIfAvailable(), postId);
    }

    /** 评论ID (String) 是否可能存在 */
    public boolean containsComment(String commentId) {
        return contains(commentBloomFilterProvider.getIfAvailable(), commentId);
    }

    /** 兼容 gateway 等 Object id 场景：自动按 Long / String 分派。 */
    public boolean containsPost(Object postId) {
        if (postId == null) return true;
        if (postId instanceof Long l) return containsPost(l);
        if (postId instanceof String s) {
            try {
                return containsPost(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
                return true;
            }
        }
        if (postId instanceof Number n) return containsPost(n.longValue());
        return true;
    }

    public boolean containsUser(Object userId) {
        if (userId == null) return true;
        if (userId instanceof Long l) return containsUser(l);
        if (userId instanceof String s) {
            try {
                return containsUser(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
                return true;
            }
        }
        if (userId instanceof Number n) return containsUser(n.longValue());
        return true;
    }

    public boolean containsComment(Object commentId) {
        if (commentId == null) return true;
        if (commentId instanceof String s) return containsComment(s);
        return containsComment(String.valueOf(commentId));
    }

    // —— 写入 ——

    public boolean addUser(Long userId) {
        return add(userBloomFilterProvider.getIfAvailable(), userId);
    }

    public boolean addPost(Long postId) {
        return add(postBloomFilterProvider.getIfAvailable(), postId);
    }

    public boolean addComment(String commentId) {
        return add(commentBloomFilterProvider.getIfAvailable(), commentId);
    }

    // —— 取原始过滤器（gateway reactive 场景手动调度）——

    public RBloomFilter<Long> getUserFilter() {
        return userBloomFilterProvider.getIfAvailable();
    }

    public RBloomFilter<Long> getPostFilter() {
        return postBloomFilterProvider.getIfAvailable();
    }

    public RBloomFilter<String> getCommentFilter() {
        return commentBloomFilterProvider.getIfAvailable();
    }
}
