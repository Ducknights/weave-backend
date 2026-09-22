package com.weave.redis.aspect;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weave.model.util.CacheKeyUtil;
import com.weave.redis.annotation.RedisCacheEvict;
import com.weave.redis.annotation.RedisCachePut;
import com.weave.redis.annotation.RedisCacheable;
import com.weave.redis.constant.CacheKey;
import com.weave.redis.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Aspect
@RequiredArgsConstructor
public class CacheAspect {

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;

    /**
     * 基于 @RedisCacheable 的缓存切面
     */
    @Around("@annotation(redisCacheable)")
    public Object around(
            ProceedingJoinPoint joinPoint,
            RedisCacheable redisCacheable) throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String redisKey = buildRedisKey(joinPoint, redisCacheable.value(), redisCacheable.key());

        // 获取方法的返回类型
        Type type = signature.getMethod().getGenericReturnType();
        JavaType javaType = objectMapper
                .getTypeFactory()
                .constructType(type);

        Object cache = redisUtil.get(redisKey, javaType);
        // 缓存命中直接返回
        if (cache != null) return cache;

        // Redis 有 Key，但值是 NULL_VALUE
        if (redisUtil.hasKey(redisKey)) return null;

        // 缓存未命中，上锁后执行目标方法
        RLock lock = redissonClient.getLock(CacheKeyUtil.buildLockKey(redisKey));
        boolean locked = false;
        try {
            locked = lock.tryLock();
            // 如果获取锁失败，等待50毫秒后重试
            if (!locked) {
                Thread.sleep(50);
                return redisUtil.get(redisKey, javaType);
            }
            // 获取锁成功，再次检测是否有缓存
            cache = redisUtil.get(redisKey, javaType);
            if (cache != null) return cache;
            if (redisUtil.hasKey(redisKey)) return null;

            // 执行目标方法
            Object result = joinPoint.proceed();

            // 如果结果为 null，缓存一个“空值哨兵”5分钟
            if (result == null){
                redisUtil.setNullValue(redisKey, Duration.ofMinutes(5));
                return null;
            }else{
                // 默认缓存1小时
                Duration ttl = redisCacheable.expire() > 0
                        ? Duration.ofSeconds(redisCacheable.expire())
                        : Duration.ofSeconds(3600);
                redisUtil.set(redisKey, result, ttl, redisCacheable.isRandom());
            }
            return result;
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw new RuntimeException("缓存加载被中断", e);
        }finally {
            if (locked && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    /**
     * 基于 @RedisCachePut 的缓存切面
     */
    @Around("@annotation(redisCachePut)")
    public Object aroundCachePut(
            ProceedingJoinPoint joinPoint,
            RedisCachePut redisCachePut) throws Throwable {

        String redisKey = buildRedisKey(joinPoint, redisCachePut.value(), redisCachePut.key());

        // 直接上锁
        RLock lock = redissonClient.getLock(CacheKeyUtil.buildLockKey(redisKey));
        // 最多等待3秒获取锁
        boolean locked = lock.tryLock(3, TimeUnit.SECONDS);
        if (!locked) {
            throw new RuntimeException("当前系统繁忙，请稍后重试");
        }
        try {
            // 先执行目标方法
            Object result = joinPoint.proceed();

            // 将结果更新到缓存
            if (result == null) {
                redisUtil.setNullValue(redisKey, Duration.ofMinutes(5));
            } else {
                Duration ttl = redisCachePut.expire() > 0
                        ? Duration.ofSeconds(redisCachePut.expire())
                        : Duration.ofSeconds(3600);
                redisUtil.set(redisKey, result, ttl, redisCachePut.isRandom());
            }

            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("缓存更新被中断", e);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    /**
     * 基于 @RedisCacheEvict 的缓存切面
     */
    @Around("@annotation(redisCacheEvent)")
    public Object aroundCacheEvent(
            ProceedingJoinPoint joinPoint,
            RedisCacheEvict redisCacheEvent) throws Throwable {

        // 先执行目标方法
        Object result = joinPoint.proceed();

        // 清除缓存
        String redisKey = buildRedisKey(joinPoint, redisCacheEvent.value(), redisCacheEvent.key());
        redisUtil.delete(redisKey);

        return result;
    }

    /**
     * 解析 SpEL 表达式，构建完整的 Redis key
     */
    private String buildRedisKey(ProceedingJoinPoint joinPoint, String prefix, String spelExpression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        String key = parser.parseExpression(spelExpression).getValue(context, String.class);
        return CacheKeyUtil.buildCacheKey(prefix, key);
    }
}
