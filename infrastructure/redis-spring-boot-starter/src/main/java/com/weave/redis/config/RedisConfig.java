package com.weave.redis.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.weave.redis.util.RedisUtil;
import com.weave.redis.util.handler.*;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JacksonCodec;
import org.redisson.codec.JsonCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * redis 使用的 ObjectMapper
     */
    @Bean("redisObjectMapper")
    @ConditionalOnMissingBean(name = "redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(new Jdk8Module())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /**
     * RedisJSON 默认 codec Bean，复用 redisObjectMapper
     */
    @Bean
    @ConditionalOnMissingBean(JsonCodec.class)
    public JsonCodec redisJsonCodec(ObjectMapper redisObjectMapper) {
        return new JacksonCodec<>(redisObjectMapper, Object.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisHelper redisHelper() {
        return new RedisHelper();
    }

    @Bean
    @ConditionalOnMissingBean
    public StringRedisHandler stringRedisHandler(StringRedisTemplate stringRedisTemplate,
                                                  ObjectMapper redisObjectMapper,
                                                  RedisHelper redisHelper) {
        return new StringRedisHandler(stringRedisTemplate, redisObjectMapper, redisHelper);
    }

    @Bean
    @ConditionalOnMissingBean
    public SetRedisHandler setRedisHandler(StringRedisTemplate stringRedisTemplate,
                                           RedisHelper redisHelper) {
        return new SetRedisHandler(stringRedisTemplate, redisHelper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ZSetRedisHandler zSetRedisHandler(StringRedisTemplate stringRedisTemplate,
                                              RedissonClient redissonClient,
                                              RedisHelper redisHelper) {
        return new ZSetRedisHandler(stringRedisTemplate, redissonClient, redisHelper);
    }

    @Bean
    @ConditionalOnMissingBean
    public JsonRedisHandler jsonRedisHandler(RedissonClient redissonClient,
                                              ObjectMapper redisObjectMapper,
                                              RedisHelper redisHelper,
                                              JsonCodec defaultJsonCodec) {
        return new JsonRedisHandler(redissonClient, redisObjectMapper, redisHelper, defaultJsonCodec);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisUtil redisUtil(StringRedisHandler stringRedisHandler,
                               SetRedisHandler setRedisHandler,
                               ZSetRedisHandler zSetRedisHandler,
                               JsonRedisHandler jsonRedisHandler) {
        return new RedisUtil(stringRedisHandler, setRedisHandler, zSetRedisHandler, jsonRedisHandler);
    }
}
