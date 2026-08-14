package com.weave.redis.config;

import com.weave.redis.constant.CacheKey;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(RedissonClient.class)
public class BloomConfig {

    @Bean(destroyMethod = "")
    @ConditionalOnProperty(prefix = "redis.bloom.user-filter", name = "enabled", havingValue = "false", matchIfMissing = true)
    public RBloomFilter<Long> userBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<Long> filter = redissonClient.getBloomFilter(CacheKey.BLOOM_USER_FILTER);
        filter.tryInit(1_000_000L, 0.001);
        return filter;
    }

    @Bean(destroyMethod = "")
    @ConditionalOnProperty(prefix = "redis.bloom.post-filter", name = "enabled", havingValue = "false", matchIfMissing = true)
    public RBloomFilter<Long> postBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<Long> filter = redissonClient.getBloomFilter(CacheKey.BLOOM_POST_FILTER);
        filter.tryInit(1_000_000L, 0.001);
        return filter;
    }

    @Bean(destroyMethod = "")
    @ConditionalOnProperty(prefix = "redis.bloom.comment-filter", name = "enabled", havingValue = "false", matchIfMissing = true)
    public RBloomFilter<String> commentBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> filter = redissonClient.getBloomFilter(CacheKey.BLOOM_COMMENT_FILTER);
        filter.tryInit(5_000_000L, 0.001);
        return filter;
    }
}
