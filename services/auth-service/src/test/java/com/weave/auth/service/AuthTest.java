package com.weave.auth.service;

import com.weave.redis.constant.CacheKey;
import com.weave.auth.model.dto.VerifyCodeDto;
import com.weave.redis.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AuthTest {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private AuthService authService;

    @Autowired
    private CacheTestService cacheTestService;

    // ==================== 原有测试 ====================

    /**
     * 保存验证码，用于测试注册功能
     */
    @Test
    public void testLogin() {
        String key = CacheKey.buildCacheKey(CacheKey.CAPTCHA, "2897662424@qq.com");
        redisUtil.set(key, 123456, Duration.ofMinutes(5));
    }

    /**
     * 直接添加一些用户
     */
    @Test
    public void testInsertUser() {
        String emailSuffix = "@qq.com";
        for (int i = 1; i < 100; i++){
            long emailHead = 2897662424L + i;
            String email = emailHead + emailSuffix;
            System.out.println(email);
            authService.register(new VerifyCodeDto(email, "123456", 123456));
        }
    }

    // ==================== @RedisCacheable 测试 ====================

    /**
     * 第一次调用走 DB（缓存未命中），第二次调用走缓存（不穿透到 DB）
     */
    @Test
    public void testCacheable_Hit() {
        Long id = 1L;
        String redisKey = CacheTestService.CACHE_PREFIX + "::" + id;

        // 清理缓存
        redisUtil.delete(redisKey);
        cacheTestService.resetCounters();
        cacheTestService.setDb(id, "hello");

        // 第一次调用：缓存未命中，穿透到方法
        String result1 = cacheTestService.getById(id);
        assertEquals("hello", result1);
        assertEquals(1, cacheTestService.getCacheableCallCount(), "第一次调用应穿透到方法");

        // 第二次调用：应命中缓存
        String result2 = cacheTestService.getById(id);
        assertEquals("hello", result2);
        assertEquals(1, cacheTestService.getCacheableCallCount(), "第二次调用应从缓存返回，不应穿透到方法");
    }

    /**
     * 缓存命中后，即使 DB 数据被外部修改，仍返回缓存中的旧值
     */
    @Test
    public void testCacheable_StaleCache() {
        Long id = 2L;
        String redisKey = CacheTestService.CACHE_PREFIX + "::" + id;

        redisUtil.delete(redisKey);
        cacheTestService.resetCounters();
        cacheTestService.setDb(id, "old-value");

        // 先触发缓存
        cacheTestService.getById(id);
        assertEquals(1, cacheTestService.getCacheableCallCount());

        // 模拟外部直接修改 DB（不走缓存注解）
        cacheTestService.setDb(id, "new-value");

        // 再次调用：应命中缓存，返回旧值
        String result = cacheTestService.getById(id);
        assertEquals("old-value", result, "缓存命中应返回旧值，不应受 DB 修改影响");
        assertEquals(1, cacheTestService.getCacheableCallCount());
    }

    // ==================== @RedisCachePut 测试 ====================

    /**
     * 调用 @RedisCachePut 后，缓存被更新
     */
    @Test
    public void testCachePut_Update() {
        Long id = 3L;
        String redisKey = CacheTestService.CACHE_PREFIX + "::" + id;

        redisUtil.delete(redisKey);
        cacheTestService.resetCounters();
        cacheTestService.setDb(id, "initial");

        // 先通过 getById 建立缓存
        cacheTestService.getById(id);
        assertEquals(1, cacheTestService.getCacheableCallCount());

        // 通过 @RedisCachePut 更新
        String result = cacheTestService.updateById(id, "updated");
        assertEquals("updated", result);
        assertEquals(1, cacheTestService.getCachePutCallCount());

        // 再次 getById：应命中更新后的缓存，不穿透到方法
        String cached = cacheTestService.getById(id);
        assertEquals("updated", cached);
        assertEquals(1, cacheTestService.getCacheableCallCount(), "缓存已被 @RedisCachePut 更新，不应再穿透");
    }

    /**
     * @RedisCachePut 总是会执行方法（即使缓存已有值）
     */
    @Test
    public void testCachePut_AlwaysExecutes() {
        Long id = 4L;
        String redisKey = CacheTestService.CACHE_PREFIX + "::" + id;

        redisUtil.delete(redisKey);
        cacheTestService.resetCounters();

        // 连续调用三次，每次都执行方法
        cacheTestService.updateById(id, "v1");
        cacheTestService.updateById(id, "v2");
        cacheTestService.updateById(id, "v3");

        assertEquals(3, cacheTestService.getCachePutCallCount(), "@RedisCachePut 应每次都执行方法");
    }

    // ==================== @RedisCacheEvent 测试 ====================

    /**
     * 调用 @RedisCacheEvent 后，缓存被清除，下次调用穿透到方法
     */
    @Test
    public void testCacheEvent_Evict() {
        Long id = 5L;
        String redisKey = CacheTestService.CACHE_PREFIX + "::" + id;

        redisUtil.delete(redisKey);
        cacheTestService.resetCounters();
        cacheTestService.setDb(id, "data");

        // 先建立缓存
        cacheTestService.getById(id);
        assertEquals(1, cacheTestService.getCacheableCallCount());

        // 调用 @RedisCacheEvent 清除缓存
        String deleted = cacheTestService.deleteById(id);
        assertEquals("data", deleted);
        assertEquals(1, cacheTestService.getCacheEventCallCount());

        // 再次 getById：缓存已清，应穿透到方法
        String result = cacheTestService.getById(id);
        assertNull(result, "DB 数据已被 deleteById 移除，应返回 null");
        assertEquals(2, cacheTestService.getCacheableCallCount(), "缓存已被清除，应再次穿透到方法");
    }

    // ==================== 复杂对象序列化/反序列化测试 ====================

    /**
     * 验证复杂对象（嵌套对象、List、Map、LocalDateTime）能正确序列化到 Redis 并反序列化
     */
    @Test
    public void testComplexObject_CacheHit() {
        Long id = 100L;
        String redisKey = CacheTestService.CACHE_PREFIX + ":user::" + id;

        redisUtil.delete(redisKey);

        // 构建复杂对象
        TestUserDto dto = new TestUserDto(
                id, "张三", 25, "zhangsan@test.com", true, 98.5,
                java.time.LocalDateTime.of(2025, 1, 1, 12, 0),
                java.util.List.of("Java", "Spring", "Redis"),
                java.util.List.of(
                        new TestUserDto.Address("广东省", "深圳市", "南山区科技园", 518000),
                        new TestUserDto.Address("北京市", "北京市", "朝阳区望京", 100000)
                ),
                java.util.Map.of("level", 5, "vip", true, "points", 1200L)
        );

        cacheTestService.setUserDb(id, dto);

        // 第一次调用：穿透
        TestUserDto result1 = cacheTestService.getComplexUser(id);
        assertEquals("张三", result1.getName());
        assertEquals(25, result1.getAge());
        assertEquals(2, result1.getAddresses().size());
        assertEquals("深圳市", result1.getAddresses().get(0).getCity());
        assertEquals(3, result1.getTags().size());
        assertEquals(5, result1.getExtInfo().get("level"));
        assertEquals(1200L, result1.getExtInfo().get("points"));
        assertEquals(1, cacheTestService.getComplexUserCallCount());

        // 第二次调用：应命中缓存（反序列化正确）
        TestUserDto result2 = cacheTestService.getComplexUser(id);
        assertNotNull(result2);
        assertEquals("张三", result2.getName());
        assertEquals("zhangsan@test.com", result2.getEmail());
        assertTrue(result2.getActive());
        assertEquals(98.5, result2.getScore());
        assertEquals(java.time.LocalDateTime.of(2025, 1, 1, 12, 0), result2.getCreateTime());
        assertEquals(2, result2.getAddresses().size());
        assertEquals("北京市", result2.getAddresses().get(1).getCity());
        assertEquals(100000, result2.getAddresses().get(1).getZipCode());
        assertEquals(3, result2.getTags().size());
        assertEquals(1, cacheTestService.getComplexUserCallCount(), "应命中缓存，不穿透");
    }
}
