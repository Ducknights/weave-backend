package com.weave.redis.util.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.redisson.api.*;
import org.redisson.codec.JacksonCodec;
import org.redisson.codec.JsonCodec;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 处理 RedisJSON 文档操作（基于 Redisson RJsonBucket）
 */
@Log4j2
@RequiredArgsConstructor
public class JsonRedisHandler {

    private final RedissonClient redissonClient;
    @Qualifier("redisObjectMapper")
    private final ObjectMapper objectMapper;
    private final RedisHelper redisHelper;
    private final JsonCodec defaultJsonCodec;

    /**
     * 设置缓存，以 RedisJSON 文档形式存储, 并设置过期时间(默认设置随机偏移量)
     */
    public <T> void setForRedisJson(String key, T obj, Duration duration) {
        RJsonBucket<T> bucket = redissonClient.getJsonBucket(key, defaultJsonCodec);;
        bucket.set(obj, redisHelper.randomDuration(duration));
    }

    /**
     * 批量 设置缓存，以 RedisJSON 文档形式存储, 并设置过期时间(默认设置随机偏移量)
     */
    public <T> void setForRedisJson(Map<String, T> map, Duration duration) {
        RBatch batch = redissonClient.createBatch();
        for (Map.Entry<String, T> entry : map.entrySet()) {
            // 获取 key 和 value
            String key = entry.getKey();
            T value = entry.getValue();
            // 创建 RJsonBucketAsync 对象并设置缓存
            RJsonBucketAsync<T> bucket = batch.getJsonBucket(key, defaultJsonCodec);
            bucket.setAsync(value, redisHelper.randomDuration(duration));
        }
        batch.execute();
    }

    /**
     * 获取缓存，将 RedisJSON 文档反序列化为对象
     */
    public <T> T getForRedisJson(String key, TypeReference<T> typeReference) {
        JsonCodec codec = new JacksonCodec<>(objectMapper, typeReference);
        RJsonBucket<T> bucket = redissonClient.getJsonBucket(key, codec);
        return bucket.get(codec);
    }

    /**
     * 批量获取缓存，将 RedisJSON 文档反序列化为对象
     */
    public <T> Map<String, T> getForRedisJson(List<String> keys, TypeReference<T> typeReference) {
        RBatch batch = redissonClient.createBatch();
        List<RFuture<T>> futures = new ArrayList<>();
        JacksonCodec<T> codec = new JacksonCodec<>(objectMapper, typeReference);
        for (String key : keys) {
            RJsonBucketAsync<T> bucket = batch.getJsonBucket(key, codec);
            futures.add(bucket.getAsync());
        }

        batch.execute();

        Map<String, T> result = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            try {
                T value = futures.get(i).get();
                if (value != null) {
                    result.put(keys.get(i), value);
                }
            } catch (Exception e) {
                log.error("获取缓存失败, key: {}", keys.get(i), e);
            }
        }
        return result;
    }

    /**
     * 原子自增 JSON 文档中的数值字段
     */
    public void incrementHash(String key, String field, long delta) {
        RJsonBucket<Long> bucket = redissonClient.getJsonBucket(key, defaultJsonCodec);
        bucket.incrementAndGet(field, delta);
    }
}
