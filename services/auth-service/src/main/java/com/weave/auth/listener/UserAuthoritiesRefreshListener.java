package com.weave.auth.listener;

import com.weave.auth.event.UserAuthoritiesRefreshEvent;
import com.weave.auth.mapper.AuthMapper;
import com.weave.auth.model.dto.CustomUserDetails;
import com.weave.redis.constant.CacheKey;
import com.weave.redis.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;

import static com.weave.auth.model.constans.CaCheTTL.USER_AUTHORITY_TTL_MINUTES;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserAuthoritiesRefreshListener {

    private final AuthMapper authMapper;
    private final RedisUtil redisUtil;

    @Async
    @EventListener
    public void handleUserAuthoritiesRefresh(UserAuthoritiesRefreshEvent event) {
        Long userId = event.getUserId();
        try {
            CustomUserDetails userDetails = authMapper.selectUserDetailsById(userId);
            if (userDetails == null) {
                log.error("刷新用户权限缓存失败，用户不存在: userId={}", userId);
                return;
            }
            String cacheKey = CacheKey.buildCacheKey(CacheKey.USER_AUTHORITY, userId);
            redisUtil.setFixed(cacheKey, userDetails, Duration.ofMinutes(USER_AUTHORITY_TTL_MINUTES));
            log.info("已刷新用户权限缓存: userId={}", userId);
        } catch (Exception e) {
            log.error("刷新用户权限缓存异常: userId={}", userId, e);
        }
    }
}
