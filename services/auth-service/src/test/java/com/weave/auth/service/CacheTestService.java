package com.weave.auth.service;

import com.weave.redis.annotation.RedisCacheEvict;
import com.weave.redis.annotation.RedisCachePut;
import com.weave.redis.annotation.RedisCacheable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 用于测试三种缓存注解的模拟 Service
 */
@Service
public class CacheTestService {

    public static final String CACHE_PREFIX = "test:cache";

    /** 模拟数据库，key=id, value=数据 */
    private final Map<Long, String> mockDb = new ConcurrentHashMap<>();

    /** 记录 @RedisCacheable 方法实际被调用的次数（即未命中缓存穿透到方法的次数） */
    private final AtomicInteger cacheableCallCount = new AtomicInteger(0);

    /** 记录 @RedisCachePut 方法实际被调用的次数 */
    private final AtomicInteger cachePutCallCount = new AtomicInteger(0);

    /** 记录 @RedisCacheEvict 方法实际被调用的次数 */
    private final AtomicInteger cacheEventCallCount = new AtomicInteger(0);

    // ==================== getter（必须通过方法访问，CGLIB 代理下直接访问字段会为 null） ====================

    public int getCacheableCallCount() { return cacheableCallCount.get(); }
    public int getCachePutCallCount() { return cachePutCallCount.get(); }
    public int getCacheEventCallCount() { return cacheEventCallCount.get(); }

    /** 模拟数据库存储复杂对象 */
    private final Map<Long, TestUserDto> mockUserDb = new ConcurrentHashMap<>();

    // ==================== 业务方法 ====================

    /**
     * 读操作 —— @RedisCacheable：先查缓存，缓存未命中才执行方法
     */
    @RedisCacheable(value = CACHE_PREFIX, key = "#id", expire = 60)
    public String getById(Long id) {
        cacheableCallCount.incrementAndGet();
        return mockDb.getOrDefault(id, "default-" + id);
    }

    /**
     * 写操作 —— @RedisCachePut：总是执行方法，并将结果写入缓存
     */
    @RedisCachePut(value = CACHE_PREFIX, key = "#id", expire = 60)
    public String updateById(Long id, String newValue) {
        cachePutCallCount.incrementAndGet();
        mockDb.put(id, newValue);
        return newValue;
    }

    /**
     * 删除操作 —— @RedisCacheEvict：执行方法后清除缓存
     */
    @RedisCacheEvict(value = CACHE_PREFIX, key = "#id")
    public String deleteById(Long id) {
        cacheEventCallCount.incrementAndGet();
        return mockDb.remove(id);
    }

    /** 直接写数据库，不走缓存，用于准备测试数据 */
    public void setDb(Long id, String value) {
        mockDb.put(id, value);
    }

    /** 重置所有计数器 */
    public void resetCounters() {
        cacheableCallCount.set(0);
        cachePutCallCount.set(0);
        cacheEventCallCount.set(0);
    }

    /** 预设复杂对象 */
    public void setUserDb(Long id, TestUserDto dto) {
        mockUserDb.put(id, dto);
    }

    // ==================== 复杂对象缓存测试 ====================

    /** 记录 getComplexUser 方法的穿透次数 */
    private final AtomicInteger complexUserCallCount = new AtomicInteger(0);
    public int getComplexUserCallCount() { return complexUserCallCount.get(); }

    /**
     * 返回复杂对象 —— 验证 JSON 序列化/反序列化（含嵌套对象、List、Map、LocalDateTime）
     */
    @RedisCacheable(value = CACHE_PREFIX + ":user", key = "#id", expire = 60)
    public TestUserDto getComplexUser(Long id) {
        complexUserCallCount.incrementAndGet();
        return mockUserDb.get(id);
    }

    @RedisCachePut(value = CACHE_PREFIX + ":user", key = "#id", expire = 60)
    public TestUserDto updateComplexUser(Long id, TestUserDto dto) {
        mockUserDb.put(id, dto);
        return dto;
    }

    @RedisCacheEvict(value = CACHE_PREFIX + ":user", key = "#id")
    public void deleteComplexUser(Long id) {
        mockUserDb.remove(id);
    }
}
