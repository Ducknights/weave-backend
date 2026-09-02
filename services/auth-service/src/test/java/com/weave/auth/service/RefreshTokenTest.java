package com.weave.auth.service;

import com.weave.auth.exception.BusinessException;
import com.weave.auth.feign.UserFeignClient;
import com.weave.auth.mapper.AuthMapper;
import com.weave.auth.model.dto.CustomUserDetails;
import com.weave.auth.model.dto.TokenDto;
import com.weave.auth.model.enums.AuthApiStatus;
import com.weave.rabbitmq.util.MQUtil;
import com.weave.redis.util.RedisUtil;
import com.weave.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;

import static com.weave.auth.model.constans.CaCheTTL.ACCESS_TOKEN_TTL_MILLIS;
import static com.weave.auth.model.constans.CaCheTTL.REFRESH_TOKEN_TTL_MILLIS;
import static com.weave.auth.model.constans.CaCheTTL.USER_AUTHORITY_TTL_MINUTES;
import static com.weave.redis.constant.CacheKey.USER_AUTHORITY;
import static com.weave.redis.constant.CacheKey.buildCacheKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 中 access/refresh token 生成与刷新相关逻辑的单元测试。
 * 用 Mockito 隔离 Spring 上下文；JwtUtil 的静态方法用 {@link MockedStatic} 替换。
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private SecurityUserDetailsService service;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthMapper authMapper;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private MQUtil mqUtil;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private RedissonClient redissonClient;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("getAccessToken：成功返回仅含 access_token 的 TokenDto")
    void getAccessToken_success_returnsTokenDto() {
        Long userId = 1001L;
        String subject = buildCacheKey(USER_AUTHORITY, userId);

        try (MockedStatic<JwtUtil> jwtMock = mockStatic(JwtUtil.class)) {
            jwtMock.when(() -> JwtUtil.generateJwtToken(subject, ACCESS_TOKEN_TTL_MILLIS))
                    .thenReturn("access-token-xxx");

            TokenDto dto = authService.getAccessToken(userId);

            assertEquals("Bearer", dto.token_type());
            assertEquals("access-token-xxx", dto.access_token());
            assertEquals(ACCESS_TOKEN_TTL_MILLIS, dto.access_token_expires_in());

            jwtMock.verify(() -> JwtUtil.generateJwtToken(subject, ACCESS_TOKEN_TTL_MILLIS), times(1));
        }
    }

    @Test
    @DisplayName("getRefreshToken：成功返回 refresh token 字符串")
    void getRefreshToken_success_returnsTokenString() {
        Long userId = 2002L;
        String subject = buildCacheKey(USER_AUTHORITY, userId);

        try (MockedStatic<JwtUtil> jwtMock = mockStatic(JwtUtil.class)) {
            jwtMock.when(() -> JwtUtil.generateJwtToken(subject, REFRESH_TOKEN_TTL_MILLIS))
                    .thenReturn("refresh-token-yyy");

            String token = authService.getRefreshToken(userId);

            assertEquals("refresh-token-yyy", token);
            jwtMock.verify(() -> JwtUtil.generateJwtToken(subject, REFRESH_TOKEN_TTL_MILLIS), times(1));
        }
    }

    @Test
    @DisplayName("getNewAccessToken：剩余有效期 < 轮换阈值，轮换 refresh 并刷新权限缓存")
    void refreshToken_withinRotationThreshold_rotatesAndRefreshesCache() {
        Long userId = 3003L;
        String subject = buildCacheKey(USER_AUTHORITY, userId);
        String oldRefresh = "old-refresh-token";
        CustomUserDetails userDetails = new CustomUserDetails(
                userId, "test@test.com", "pwd",
                List.of("USER"), List.of("read"), "USER", "read");

        try (MockedStatic<JwtUtil> jwtMock = mockStatic(JwtUtil.class)) {
            jwtMock.when(() -> JwtUtil.getUserIdFromJWT(oldRefresh)).thenReturn(String.valueOf(userId));
            jwtMock.when(() -> JwtUtil.generateJwtToken(subject, ACCESS_TOKEN_TTL_MILLIS)).thenReturn("new-access");
            jwtMock.when(() -> JwtUtil.getExpirationFromJWT(oldRefresh)).thenReturn(1_000L * 60 * 60 * 24);
            jwtMock.when(() -> JwtUtil.generateJwtToken(subject, REFRESH_TOKEN_TTL_MILLIS)).thenReturn("new-refresh");
            when(authMapper.selectUserDetailsById(userId)).thenReturn(userDetails);

            TokenDto result = authService.getNewAccessToken(oldRefresh);

            assertNotNull(result);

            jwtMock.verify(() -> JwtUtil.getUserIdFromJWT(oldRefresh), times(1));
            jwtMock.verify(() -> JwtUtil.generateJwtToken(subject, ACCESS_TOKEN_TTL_MILLIS), times(1));
            jwtMock.verify(() -> JwtUtil.getExpirationFromJWT(oldRefresh), times(1));
            jwtMock.verify(() -> JwtUtil.generateJwtToken(subject, REFRESH_TOKEN_TTL_MILLIS), times(1));
            verify(authMapper, times(1)).selectUserDetailsById(userId);
            verify(redisUtil, times(1)).set(eq(subject), eq(userDetails),
                    eq(Duration.ofMinutes(USER_AUTHORITY_TTL_MINUTES)));
        }
    }

    @Test
    @DisplayName("getNewAccessToken：剩余有效期 ≥ 轮换阈值，不轮换 refresh 但仍刷新权限缓存")
    void refreshToken_beyondRotationThreshold_returnsWithoutNewRefresh() {
        Long userId = 4004L;
        String subject = buildCacheKey(USER_AUTHORITY, userId);
        String oldRefresh = "old-refresh-token";
        CustomUserDetails userDetails = new CustomUserDetails(
                userId, "beyond@test.com", "pwd",
                List.of("USER"), null, "USER", null);

        try (MockedStatic<JwtUtil> jwtMock = mockStatic(JwtUtil.class)) {
            jwtMock.when(() -> JwtUtil.getUserIdFromJWT(oldRefresh)).thenReturn(String.valueOf(userId));
            jwtMock.when(() -> JwtUtil.generateJwtToken(subject, ACCESS_TOKEN_TTL_MILLIS)).thenReturn("new-access");
            jwtMock.when(() -> JwtUtil.getExpirationFromJWT(oldRefresh)).thenReturn(1_000L * 60 * 60 * 24 * 3);
            when(authMapper.selectUserDetailsById(userId)).thenReturn(userDetails);

            TokenDto result = authService.getNewAccessToken(oldRefresh);

            assertNotNull(result);

            jwtMock.verify(() -> JwtUtil.generateJwtToken(subject, REFRESH_TOKEN_TTL_MILLIS), never());
            verify(authMapper, times(1)).selectUserDetailsById(userId);
            verify(redisUtil, times(1)).set(eq(subject), eq(userDetails),
                    eq(Duration.ofMinutes(USER_AUTHORITY_TTL_MINUTES)));
        }
    }

    @Test
    @DisplayName("getNewAccessToken：JWT 解析失败，外层 catch 包成 TOKEN_GENERATE_FAILED")
    void refreshToken_jwtParseFails_throwsTokenGenerateFailed() {
        String oldRefresh = "invalid-token";

        try (MockedStatic<JwtUtil> jwtMock = mockStatic(JwtUtil.class)) {
            jwtMock.when(() -> JwtUtil.getUserIdFromJWT(oldRefresh))
                    .thenThrow(new RuntimeException("Token解析错误"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.getNewAccessToken(oldRefresh));

            assertEquals(AuthApiStatus.TOKEN_GENERATE_FAILED, ex.getStatus());

            jwtMock.verify(() -> JwtUtil.generateJwtToken(anyString(), anyInt()), never());
            verify(authMapper, never()).selectUserDetailsById(anyLong());
            verify(redisUtil, never()).set(anyString(), any(), any(Duration.class));
        }
    }

    @Test
    @DisplayName("getNewAccessToken：cacheUserAuthorities 找不到用户，抛 TOKEN_GENERATE_FAILED")
    void refreshToken_userNotFound_throwsTokenGenerateFailed() {
        Long userId = 5005L;
        String subject = buildCacheKey(USER_AUTHORITY, userId);
        String oldRefresh = "old-refresh-token";

        try (MockedStatic<JwtUtil> jwtMock = mockStatic(JwtUtil.class)) {
            jwtMock.when(() -> JwtUtil.getUserIdFromJWT(oldRefresh)).thenReturn(String.valueOf(userId));
            jwtMock.when(() -> JwtUtil.generateJwtToken(subject, ACCESS_TOKEN_TTL_MILLIS)).thenReturn("new-access");
            jwtMock.when(() -> JwtUtil.getExpirationFromJWT(oldRefresh)).thenReturn(1_000L * 60 * 60);
            when(authMapper.selectUserDetailsById(userId)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.getNewAccessToken(oldRefresh));

            assertEquals(AuthApiStatus.TOKEN_GENERATE_FAILED, ex.getStatus());

            verify(authMapper, times(1)).selectUserDetailsById(userId);
            verify(redisUtil, never()).set(anyString(), any(), any(Duration.class));
        }
    }
}
