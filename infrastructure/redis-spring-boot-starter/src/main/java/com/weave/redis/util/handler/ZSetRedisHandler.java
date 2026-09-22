package com.weave.redis.util.handler;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

/**
 * 处理 Redis ZSet 类型操作
 */
@RequiredArgsConstructor
public class ZSetRedisHandler {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final RedisHelper redisHelper;

    /**
     * 将 Map<成员, 分数>批量添加到 ZSet
     */
    public void addToZSet(String key, Map<?, Double> membersWithScores, Duration duration) {
        Set<TypedTuple<String>> tuples = new HashSet<>();
        membersWithScores.forEach((member, score) ->
                tuples.add(new DefaultTypedTuple<>(redisHelper.convertToString(member), score))
        );
        stringRedisTemplate.opsForZSet().add(key, tuples);
        stringRedisTemplate.expire(key, redisHelper.randomDuration(duration));
    }

    /**
     * 获取 ZSet 中的成员,按照分数排序
     */
    public List<String> getMembersFromZSet(String key, long limit, boolean isDesc) {
        Set<String> set;
        // 倒序
        if (isDesc) set = stringRedisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
        // 正序
        else set = stringRedisTemplate.opsForZSet().range(key, 0, limit - 1);
        // 转换，保证有序性
        if (set == null) return new ArrayList<>();
        return new ArrayList<>(set);
    }

    /**
     * 根据游标分数获取 ZSet 中的成员,按照分数排序
     */
    public <T> List<String> getMembersFromZSetWithCursor(String key, T cursorScore, long limit, boolean isDesc ) {
        // 设置分数范围（业务范围）
        double max = Double.POSITIVE_INFINITY;
        double min = 1;

        Collection<String> result;
        RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(key);
        // 无游标，首次查询
        if (cursorScore == null) {
            if (isDesc) {
                // 降序（最新）：从 max 开始，往下查到 min
                result = set.valueRangeReversed(
                        max, true,
                        min, true,
                        0,
                        (int)limit
                );
            } else {
                // 升序（最旧）：从 min 开始，往上查到 max
                result = set.valueRange(
                        min, true,
                        max, true,
                        0,
                        (int)limit
                );
            }
        }
        // 有游标时，就用游标替换掉那一端的边界，并排除自身
        else {
            if (isDesc) {
                // 降序（最新）：从 cursorScore 开始，往下查到 min
                result = set.valueRangeReversed(
                        (Double) cursorScore, false,
                        min, true,
                        0,
                        (int)limit
                );
            } else {
                // 升序（最旧）：从 cursorScore 开始，往上查到 max
                result = set.valueRange(
                        (Double) cursorScore, false,
                        max, true,
                        0,
                        (int)limit
                );
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 从 ZSet 中移除成员
     */
    public <T> void removeFromZSet(String key, T obj, Duration duration) {
        Stream<?> stream = (obj instanceof Collection<?> collection) ? collection.stream() : Stream.of(obj);
        Object[] values = stream
                .map(redisHelper::convertToString)
                .filter(Objects::nonNull)
                .toArray(Object[]::new);
        if (values.length > 0) stringRedisTemplate.opsForZSet().remove(key, values);
        stringRedisTemplate.expire(key, duration);
    }

    /**
     * 判断成员是否在 ZSet 中
     */
    public <T> boolean isMemberOfZSet(String key, T obj) {
        String member = redisHelper.convertToString(obj);
        return member != null && stringRedisTemplate.opsForZSet().score(key, member) != null;
    }
}
