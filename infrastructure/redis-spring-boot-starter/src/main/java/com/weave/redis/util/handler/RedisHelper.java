package com.weave.redis.util.handler;

import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis 操作公共逻辑：随机过期偏移、对象序列化
 */
@RequiredArgsConstructor
public class RedisHelper {

    private static final int MAX_RANDOM_OFFSET = 10;
    private static final int MIN_RANDOM_OFFSET = 1;

    /**
     * 在原过期时间基础上叠加随机秒数，防止缓存雪崩
     */
    public Duration randomDuration(Duration duration) {
        int randomOffset = ThreadLocalRandom.current().nextInt(MIN_RANDOM_OFFSET, MAX_RANDOM_OFFSET);
        return duration.plusSeconds(randomOffset);
    }

    /**
     * 将对象转换为字符串，用于 Set/ZSet 成员存储
     */
    public String convertToString(Object item) {
        if (item instanceof String) return (String) item;
        if (item instanceof Number) return String.valueOf(item);
        else throw new RuntimeException("不支持非字符串、非数字，以及空对象");
    }
}
