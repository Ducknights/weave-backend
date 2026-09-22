package com.weave.redis.util.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * 处理 Redis Set 类型操作
 */
@RequiredArgsConstructor
public class SetRedisHandler {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisHelper redisHelper;

    /**
     * 设置缓存，序列化对象(List)为 SET
     */
    public <T> void addToSet(String key, T obj, Duration duration) {
        // 判断对象是否为集合类型
        if ((obj instanceof Collection<?> collection)) {
            String[] values = collection.stream()
                    .map(redisHelper::convertToString)
                    .filter(Objects::nonNull)
                    .toArray(String[]::new);
            if (values.length > 0) stringRedisTemplate.opsForSet().add(key, values);
        }else {
            // 处理非集合类型对象
            String value = redisHelper.convertToString(obj);
            stringRedisTemplate.opsForSet().add(key, value);
        }
        stringRedisTemplate.expire(key, duration);
    }

    /**
     * 从 SET 中移除元素
     */
    public void removeFromSet(String cacheKey, Object item, Duration duration) {
        stringRedisTemplate.opsForSet().remove(cacheKey, redisHelper.convertToString(item));
        stringRedisTemplate.expire(cacheKey, duration);
    }

    /**
     * 获取两个 SET 的并集
     */
    public Set<String> getUnion(String key1, String key2) {
        return stringRedisTemplate.opsForSet().union(key1, key2);
    }

    /**
     * 获取两个 SET 的交集
     */
    public Set<String> getIntersection(String key1, String key2) {
        return stringRedisTemplate.opsForSet().intersect(key1, key2);
    }

    /**
     * 获取两个 SET 的差集, SET 1 - SET 2
     */
    public Set<String> getDifference(String key1, String key2) {
        return stringRedisTemplate.opsForSet().difference(key1, key2);
    }

    /**
     * 获取 Set 中所有成员
     */
    public Set<String> getMembersFromSet(String key) {
        return stringRedisTemplate.opsForSet().members(key);
    }

    /**
     * 判断 SET 中是否包含指定元素
     */
    public Boolean isMember(String cacheKey, Object item) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(cacheKey, redisHelper.convertToString(item)));
    }
}
