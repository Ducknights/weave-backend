package com.weave.model.util;

import java.util.Objects;

public class CacheKeyUtil {

    private static final String KEY_SEPARATOR = ":";
    private static final String LOCK_PREFIX = "lock";

    /**
     * 构建缓存key
     */
    public static String buildCacheKey(String prefix, String identifier) {
        return Objects.requireNonNull(prefix, "前缀不能为空") + KEY_SEPARATOR + Objects.requireNonNull(identifier, "标识符不能为空");
    }

    public static String buildCacheKey(String prefix, long identifier) {
        return buildCacheKey(prefix, String.valueOf(identifier));
    }

    /**
     * 构建锁key
     */
    public static String buildLockKey(String prefix, String identifier) {
        return LOCK_PREFIX + KEY_SEPARATOR + buildCacheKey(prefix, identifier);
    }

    public static String buildLockKey(String prefix, long identifier) {
        return LOCK_PREFIX + KEY_SEPARATOR + buildCacheKey(prefix, String.valueOf(identifier));
    }

    public static String buildLockKey(String key) {
        return LOCK_PREFIX + KEY_SEPARATOR + Objects.requireNonNull(key, "key不能为空");
    }

    /**
     * 拆解缓存key
     */
    public static String demolishCacheKey(String fullKey, String prefix) {
        String prefixWithSeparator = prefix + KEY_SEPARATOR;
        if (!fullKey.startsWith(prefixWithSeparator)) {
            throw new IllegalArgumentException("前缀不匹配" + fullKey);
        }
        return fullKey.substring(prefixWithSeparator.length());
    }
}
