package com.weave.redis.util.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weave.redis.constant.CacheValue;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 处理 Redis String 类型操作
 */
@RequiredArgsConstructor
public class StringRedisHandler {

    private final StringRedisTemplate stringRedisTemplate;
    @Qualifier("redisObjectMapper")
    private final ObjectMapper redisObjectMapper;
    private final RedisHelper redisHelper;

    /**
     * 设置缓存，自动序列化对象为JSON字符串，并设置过期时间
     */
    @SneakyThrows
    public <T> void set(String key, T value, Duration duration, boolean isRandom) {
        if (isRandom) {duration = redisHelper.randomDuration(duration);}
        stringRedisTemplate.opsForValue().set(key, redisObjectMapper.writeValueAsString(value),duration);
    }

    /**
     * 批量设置缓存，自动序列化对象为JSON字符串
     */
    public <T> void set(Map<String, T> map, Duration duration, boolean isRandom) {
        // 过滤掉key为 null 的条目
        map.entrySet().removeIf(entry -> entry.getKey() == null);
        if (map.isEmpty()) {return;}

        // 使用Pipelined管道
        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Map.Entry<String, T> entry : map.entrySet()) {
                String json;
                try {
                    // 考虑缓存空值
                    if (entry.getValue() == null) {json = CacheValue.NULL_VALUE;}
                    else {json = redisObjectMapper.writeValueAsString(entry.getValue());}
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                Duration newDuration = isRandom ? duration.plusSeconds(redisHelper.randomDuration(duration).toSeconds()) : duration;
                stringRedisTemplate.opsForValue().set(entry.getKey(), json, newDuration);
            }
            return null;
        });
    }

    /**
     * 写入空值哨兵，用于防止缓存穿透
     */
    public void setNullValue(String key, Duration duration) {
        stringRedisTemplate.opsForValue().set(key, CacheValue.NULL_VALUE, duration);
    }

    /**
     * 获取缓存，自动将JSON字符串反序列化为对象
     */
    @SneakyThrows
    public <T> T get(String key, JavaType javaType) {
        String json = getJson(key);
        return json == null ? null : redisObjectMapper.readValue(json, javaType);
    }

    @SneakyThrows
    public <T> T get(String key, TypeReference<T> typeReference) {
        String json = getJson(key);
        return json == null ? null : redisObjectMapper.readValue(json, typeReference);
    }

    /**
     * 批量获取缓存，自动将JSON字符串反序列化为对象
     *<p><b style="color: #d9534f;">注意：</b>调用方务必使用 <code>map.containsKey(key)</code> 判断是否命中。
     */
    public <T> Map<String, T> get(List<String> keys, TypeReference<T> typeReference) {
        // 去空和去重
        List<String> distinctKeys = keys.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctKeys.isEmpty()) {
            return new HashMap<>();
        }

        // 批量获取
        List<String> jsonValues = stringRedisTemplate.opsForValue().multiGet(distinctKeys);

        Map<String, T> result = new HashMap<>(keys.size());
        for (int i = 0; i < distinctKeys.size(); i++) {
            String key = distinctKeys.get(i);
            String json = jsonValues != null ? jsonValues.get(i) : null;
            // 未命中直接跳过，丢弃key
            if (json == null) continue;
            // 命中空值哨兵，返回null
            if (json.equals(CacheValue.NULL_VALUE)){ result.put(key, null); continue;}
            // 命中真正缓存，反序列化为对象
            try {
                T value = redisObjectMapper.readValue(json, typeReference);
                result.put(key, value);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("反序列化失败，key: " + key, e);
            }
        }
        return result;
    }

    /**
     * 读取原始 JSON 字符串，过滤空值哨兵
     */
    private String getJson(String key) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || CacheValue.NULL_VALUE.equals(json)) {return null;}
        return json;
    }

    /**
     * 判断缓存是否存在
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /**
     * 删除缓存
     */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }
}
