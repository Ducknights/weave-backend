package com.weave.redis.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.weave.redis.util.handler.JsonRedisHandler;
import com.weave.redis.util.handler.SetRedisHandler;
import com.weave.redis.util.handler.StringRedisHandler;
import com.weave.redis.util.handler.ZSetRedisHandler;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisHandler stringRedisHandler;
    private final SetRedisHandler setRedisHandler;
    private final ZSetRedisHandler zSetRedisHandler;
    private final JsonRedisHandler jsonRedisHandler;

    /**
     * 添加
     */
    public <T> void set(String key, T value, Duration duration, boolean isRandom) {
        stringRedisHandler.set(key, value, duration, isRandom);
    }

    /**
     * 批量添加
     */
    public <T> void set(Map<String, T> map, Duration duration, boolean isRandom) {
        stringRedisHandler.set(map, duration, isRandom);
    }

    /**
     * 添加空值
     */
    public void setNullValue(String key, Duration duration) {
        stringRedisHandler.setNullValue(key, duration);
    }

    /**
     * 获取
     */
    public <T> T get(String key, JavaType javaType) {
        return stringRedisHandler.get(key, javaType);
    }
    public <T> T get(String key, TypeReference<T> typeReference) {
        return stringRedisHandler.get(key, typeReference);
    }

    /**
     * 批量获取
     */
    public <T> Map<String, T> get(List<String> keys, TypeReference<T> typeReference) {
        return stringRedisHandler.get(keys, typeReference);
    }

    /**
     * 判断是否存在
     */
    public boolean hasKey(String key) {
        return stringRedisHandler.hasKey(key);
    }

    /**
     * 删除
     */
    public void delete(String key) {
        stringRedisHandler.delete(key);
    }

    /**
     * 添加到SET
     */
    public <T> void addToSet(String key, T obj, Duration duration) {
        setRedisHandler.addToSet(key, obj, duration);
    }

    /**
     * 从SET中移除
     */
    public void removeFromSet(String cacheKey, Object item, Duration duration) {
        setRedisHandler.removeFromSet(cacheKey, item, duration);
    }

    /**
     * 获取SET的并集
     */
    public Set<String> getUnion(String key1, String key2) {
        return setRedisHandler.getUnion(key1, key2);
    }

    /**
     * 获取SET的交集
     */
    public Set<String> getIntersection(String key1, String key2) {
        return setRedisHandler.getIntersection(key1, key2);
    }

    /**
     * 获取SET的差集
     */
    public Set<String> getDifference(String key1, String key2) {
        return setRedisHandler.getDifference(key1, key2);
    }

    /**
     * 获取SET的成员
     */
    public Set<String> getMembersFromSet(String key) {
        return setRedisHandler.getMembersFromSet(key);
    }

    /**
     * 判断SET中是否存在该成员
     */
    public Boolean isMember(String cacheKey, Object item) {
        return setRedisHandler.isMember(cacheKey, item);
    }

    /**
     * 添加到ZSET
     */
    public <T> void addToZSet(String key, Map<T, Double> membersWithScores, Duration duration) {
        zSetRedisHandler.addToZSet(key, membersWithScores, duration);
    }

    public List<String> getMembersFromZSetWithCursor(String key, long limit, boolean isDesc) {
        return zSetRedisHandler.getMembersFromZSet(key, limit, isDesc);
    }

    /**
     * 获取ZSET的成员
     */
    public <T> List<String> getMembersFromZSetWithCursor(String key, T cursor, long limit, boolean isDesc) {
        return zSetRedisHandler.getMembersFromZSetWithCursor(key, cursor, limit, isDesc);
    }

    /**
     * 从ZSET中移除
     */
    public <T> void removeFromZSet(String key, T obj, Duration duration) {
        zSetRedisHandler.removeFromZSet(key, obj, duration);
    }

    /**
     * 添加到JSON
     */
    public <T> void setForRedisJson(String key, T obj, Duration duration) {
        jsonRedisHandler.setForRedisJson(key, obj, duration);
    }

    /**
     * 批量添加
     */
    public <T> void setForRedisJson(Map<String, T> map, Duration duration) {
        jsonRedisHandler.setForRedisJson(map, duration);
    }

    /**
     * 获取
     */
    public <T> T getForRedisJson(String key, TypeReference<T> typeReference) {
        return jsonRedisHandler.getForRedisJson(key, typeReference);
    }

    /**
     * 批量获取
     */
    public <T> Map<String, T> getForRedisJson(List<String> keys, TypeReference<T> typeReference) {
        return jsonRedisHandler.getForRedisJson(keys, typeReference);
    }

    /**
     * 增加一个 json对象的字段的数字值
     */
    public void incrementHash(String key, String field, long delta) {
        jsonRedisHandler.incrementHash(key, field, delta);
    }

    // TODO: Hash
}
