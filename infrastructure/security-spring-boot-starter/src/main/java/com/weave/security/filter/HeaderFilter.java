package com.weave.security.filter;

import com.weave.security.model.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.weave.security.authentication.HeaderAuthenticationToken;
import com.weave.redis.constant.CacheKey;
import com.weave.model.constant.RequestHeader;
import com.weave.redis.util.RedisUtil;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class HeaderFilter extends OncePerRequestFilter {

    private final RedisUtil redisUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userIdStr = request.getHeader(RequestHeader.X_USER_ID);
        if (userIdStr != null) {
            try {
                // 从Redis中获取用户信息
                Long userId = Long.valueOf(userIdStr);
                String key = CacheKey.buildCacheKey(CacheKey.USER_AUTHORITY, userId);
                CustomUserDetails userDetails = redisUtil.get(key, CustomUserDetails.class);
                // 如果用户信息存在，则设置到SecurityContext中
                if (userDetails != null) {
                    HeaderAuthenticationToken token = new HeaderAuthenticationToken(userDetails);
                    SecurityContextHolder.getContext().setAuthentication(token);
                    log.debug("Redis中获取用户信息: userId={}", userId);
                } else {
                    log.warn("Redis中没有用户信息: {}", userId);
                    SecurityContextHolder.clearContext();
                }
            } catch (NumberFormatException e) {
                log.warn("无效的X-UserId: {}", userIdStr);
                SecurityContextHolder.clearContext();
            } catch (Exception e) {
                log.error("Redis中用户认证信息错误", e);
                SecurityContextHolder.clearContext();
            }
        } else {
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
}
