package com.weave.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weave.auth.exception.BusinessException;
import com.weave.auth.feign.UserFeignClient;
import com.weave.auth.mapper.AuthMapper;
import com.weave.auth.model.dto.ApiRequestDto;
import com.weave.auth.model.dto.CustomUserDetails;
import com.weave.auth.model.dto.LoginResult;
import com.weave.auth.model.dto.TokenDto;
import com.weave.auth.model.dto.UserAuthDto;
import com.weave.auth.model.dto.VerifyCodeDto;
import com.weave.auth.model.enums.AuthApiStatus;
import com.weave.model.model.dto.AuthUserDto;
import com.weave.model.model.dto.UserBriefDto;
import com.weave.model.model.dto.UserDetailDto;
import com.weave.model.util.CacheKeyUtil;
import com.weave.model.util.JwtUtil;
import com.weave.rabbitmq.util.MQUtil;
import com.weave.redis.constant.CacheKey;
import com.weave.redis.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.weave.auth.model.constans.CaCheTTL.ACCESS_TOKEN_TTL_MILLIS;
import static com.weave.auth.model.constans.CaCheTTL.REFRESH_TOKEN_TTL_MILLIS;
import static com.weave.auth.model.constans.CaCheTTL.TOKEN_ROTATION_THRESHOLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 单元测试
 * mock 掉 Mapper / Feign / MQ / Redis / JWT / Redisson，不启动 Spring 容器、不连任何外部依赖。
 * objectMapper 用真实实例，因为 claims 的构造是被测行为的一部分。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Long USER_ID = 1001L;
    private static final Long NEW_USER_ID = 99L;
    private static final String EMAIL = "2897662424@qq.com";
    private static final String RAW_PASSWORD = "123456";
    private static final String ENCODED_PASSWORD = new BCryptPasswordEncoder().encode(RAW_PASSWORD);
    private static final String CAPTCHA_KEY = CacheKeyUtil.buildCacheKey(CacheKey.CAPTCHA, EMAIL);
    private static final String CAPTCHA_LOCK_KEY = CacheKeyUtil.buildLockKey(CacheKey.CAPTCHA, EMAIL);
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock
    private AuthMapper authMapper;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private MQUtil mqUtil;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;

    @Captor
    private ArgumentCaptor<Map<String, Object>> claimsCaptor;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authMapper, userFeignClient, mqUtil,
                new ObjectMapper(), redisUtil, jwtUtil, redissonClient);
    }

    // ==================== login ====================

    @Test
    @DisplayName("登录成功：返回 access token、refresh token 与用户基本信息")
    void login_shouldReturnTokensAndUserInfo_whenCredentialsValid() {
        when(authMapper.selectUserDetailsByEmail(EMAIL)).thenReturn(userDetails());
        when(jwtUtil.generateJwtToken(eq(USER_ID), anyMap(), eq(ACCESS_TOKEN_TTL_MILLIS))).thenReturn(ACCESS_TOKEN);
        when(jwtUtil.generateJwtToken(eq(USER_ID), anyMap(), eq(REFRESH_TOKEN_TTL_MILLIS))).thenReturn(REFRESH_TOKEN);
        when(userFeignClient.getUserBriefById(USER_ID)).thenReturn(new UserBriefDto(USER_ID, "张三", "avatar.png"));

        LoginResult result = authService.login(new ApiRequestDto(EMAIL, RAW_PASSWORD));

        assertEquals(REFRESH_TOKEN, result.refreshToken(), "refresh token 用于写 cookie");
        TokenDto token = result.loginResDto().token();
        assertEquals("Bearer", token.token_type());
        assertEquals(ACCESS_TOKEN, token.access_token());
        assertEquals(ACCESS_TOKEN_TTL_MILLIS, token.access_token_expires_in());
        assertEquals(USER_ID, result.loginResDto().user().getId());
        assertEquals("张三", result.loginResDto().user().getName());
        assertEquals("avatar.png", result.loginResDto().user().getAvatar());
    }

    @Test
    @DisplayName("密码错误：抛 401 LOGIN_FAILED，且不再签发令牌")
    void login_shouldFail_whenPasswordMismatch() {
        when(authMapper.selectUserDetailsByEmail(EMAIL)).thenReturn(userDetails());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new ApiRequestDto(EMAIL, "wrong-password")));

        assertEquals(AuthApiStatus.LOGIN_FAILED, ex.getStatus());

        verify(jwtUtil, never()).generateJwtToken(any(), anyMap(), any(Integer.class));
    }

    @Test
    @DisplayName("用户不存在：同样抛 LOGIN_FAILED，不区分账号是否存在")
    void login_shouldFail_whenUserNotFound() {
        when(authMapper.selectUserDetailsByEmail(EMAIL)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new ApiRequestDto(EMAIL, RAW_PASSWORD)));

        assertEquals(AuthApiStatus.LOGIN_FAILED, ex.getStatus());
    }

    // ==================== getAccessToken / getRefreshToken ====================

    @Test
    @DisplayName("getAccessToken：用户属性整体摊平成 claims，subject 只放 userId")
    void getAccessToken_shouldFlattenUserAttributesIntoClaims() {
        when(jwtUtil.generateJwtToken(eq(USER_ID), anyMap(), eq(ACCESS_TOKEN_TTL_MILLIS))).thenReturn(ACCESS_TOKEN);

        TokenDto token = authService.getAccessToken(details());

        verify(jwtUtil, times(1)).generateJwtToken(eq(USER_ID), claimsCaptor.capture(), eq(ACCESS_TOKEN_TTL_MILLIS));

        Map<String, Object> claims = claimsCaptor.getValue();
        assertEquals(USER_ID, ((Number) claims.get("userId")).longValue());
        assertEquals(List.of("USER"), claims.get("roles"));
        assertEquals(List.of("post:read"), claims.get("authorities"));
        assertEquals("Bearer", token.token_type());
        assertEquals(ACCESS_TOKEN_TTL_MILLIS, token.access_token_expires_in());
    }

    // ==================== getNewAccessToken ====================

    @Test
    @DisplayName("刷新访问令牌：refresh token 合法则直接签发新的 access token")
    void getNewAccessToken_shouldReturnAccessToken_whenRefreshTokenValid() {
        when(jwtUtil.getUserDetailFromJWT(REFRESH_TOKEN)).thenReturn(details());
        when(jwtUtil.generateJwtToken(eq(USER_ID), anyMap(), eq(ACCESS_TOKEN_TTL_MILLIS))).thenReturn("new-access");

        TokenDto token = authService.getNewAccessToken(REFRESH_TOKEN);

        assertEquals("new-access", token.access_token());
    }

    @Test
    @DisplayName("刷新访问令牌：refresh token 解析失败则抛 TOKEN_GENERATE_FAILED")
    void getNewAccessToken_shouldThrowTokenGenerateFailed_whenRefreshTokenInvalid() {
        when(jwtUtil.getUserDetailFromJWT("bad-token")).thenThrow(new RuntimeException("Token解析错误"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.getNewAccessToken("bad-token"));

        assertEquals(AuthApiStatus.TOKEN_GENERATE_FAILED, ex.getStatus());
        verify(jwtUtil, never()).generateJwtToken(any(), anyMap(), any(Integer.class));
    }

    // ==================== getNewRefreshToken ====================

    @Test
    @DisplayName("轮换刷新令牌：剩余有效期高于阈值时返回空，不轮换")
    void getNewRefreshToken_shouldReturnEmpty_whenRemainingValidityAboveThreshold() {
        when(jwtUtil.getExpirationFromJWT(REFRESH_TOKEN)).thenReturn((long) TOKEN_ROTATION_THRESHOLD + 1);

        Optional<String> result = authService.getNewRefreshToken(REFRESH_TOKEN);

        assertTrue(result.isEmpty());
        verify(jwtUtil, never()).generateJwtToken(any(), anyMap(), any(Integer.class));
    }

    @Test
    @DisplayName("轮换刷新令牌：剩余有效期低于阈值时签发新的 refresh token")
    void getNewRefreshToken_shouldRotate_whenRemainingValidityWithinThreshold() {
        when(jwtUtil.getExpirationFromJWT(REFRESH_TOKEN)).thenReturn((long) TOKEN_ROTATION_THRESHOLD - 1);
        when(jwtUtil.getUserDetailFromJWT(REFRESH_TOKEN)).thenReturn(details());
        when(jwtUtil.generateJwtToken(eq(USER_ID), anyMap(), eq(REFRESH_TOKEN_TTL_MILLIS))).thenReturn("new-refresh");

        Optional<String> result = authService.getNewRefreshToken(REFRESH_TOKEN);

        assertEquals(Optional.of("new-refresh"), result);
    }

    @Test
    @DisplayName("轮换刷新令牌：refresh token 无法解析则抛 TOKEN_GENERATE_FAILED，不裸抛 RuntimeException")
    void getNewRefreshToken_shouldThrowTokenGenerateFailed_whenRefreshTokenInvalid() {
        when(jwtUtil.getExpirationFromJWT("bad-token")).thenThrow(new RuntimeException("Token解析错误"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.getNewRefreshToken("bad-token"));

        assertEquals(AuthApiStatus.TOKEN_GENERATE_FAILED, ex.getStatus());
    }

    // ==================== sendCode ====================

    @Test
    @DisplayName("发送验证码：拿到锁后投递消息，并在 finally 中释放锁")
    void sendCode_shouldSendMessageAndReleaseLock_whenLockAcquired() throws InterruptedException {
        givenLockAcquired();

        authService.sendCode(new ApiRequestDto(EMAIL, RAW_PASSWORD));

        verify(mqUtil, times(1)).sendCaptchaCode(EMAIL);
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("发送验证码：1 分钟内重复请求拿不到锁，抛 CODE_ALREADY_SENT 且不投递消息")
    void sendCode_shouldThrowCodeAlreadySent_whenLockNotAcquired() throws InterruptedException {
        when(redissonClient.getLock(CAPTCHA_LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.sendCode(new ApiRequestDto(EMAIL, RAW_PASSWORD)));

        assertEquals(AuthApiStatus.CODE_ALREADY_SENT, ex.getStatus());
        verify(mqUtil, never()).sendCaptchaCode(anyString());
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("发送验证码：邮箱已注册则校验失败，但锁仍要释放")
    void sendCode_shouldThrowEmailAlreadyRegistered_whenEmailExists() throws InterruptedException {
        givenLockAcquired();
        when(authMapper.selectUserByEmail(EMAIL)).thenReturn(new UserAuthDto(USER_ID, EMAIL, ENCODED_PASSWORD));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.sendCode(new ApiRequestDto(EMAIL, RAW_PASSWORD)));

        assertEquals(AuthApiStatus.EMAIL_ALREADY_REGISTERED, ex.getStatus());
        verify(mqUtil, never()).sendCaptchaCode(anyString());
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("发送验证码：获取锁被中断时抛 CODE_SEND_FAILED 并恢复中断标记")
    void sendCode_shouldThrowCodeSendFailed_whenInterrupted() throws InterruptedException {
        when(redissonClient.getLock(CAPTCHA_LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenThrow(new InterruptedException());

        try {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.sendCode(new ApiRequestDto(EMAIL, RAW_PASSWORD)));

            assertEquals(AuthApiStatus.CODE_SEND_FAILED, ex.getStatus());
            assertTrue(Thread.currentThread().isInterrupted(), "应重新设置中断标记");
        } finally {
            // 清除中断标记，避免污染同一线程上的后续用例
            Thread.interrupted();
        }
    }

    // ==================== verifyCode ====================

    @Test
    @DisplayName("校验验证码：验证码不存在（已过期）则抛 CODE_EXPIRED")
    void verifyCode_shouldThrowCodeExpired_whenCodeAbsent() {
        when(redisUtil.hasKey(CAPTCHA_KEY)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.verifyCode(new VerifyCodeDto(EMAIL, RAW_PASSWORD, 123456)));

        assertEquals(AuthApiStatus.CODE_EXPIRED, ex.getStatus());
        verify(authMapper, never()).insert(any(UserAuthDto.class));
    }

    @Test
    @DisplayName("校验验证码：验证码不匹配则抛 CODE_ERROR，且不消费验证码")
    void verifyCode_shouldThrowCodeError_whenCodeMismatch() {
        when(redisUtil.hasKey(CAPTCHA_KEY)).thenReturn(true);
        when(redisUtil.get(eq(CAPTCHA_KEY), ArgumentMatchers.<TypeReference<Integer>>any())).thenReturn(700001);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.verifyCode(new VerifyCodeDto(EMAIL, RAW_PASSWORD, 123456)));

        assertEquals(AuthApiStatus.CODE_ERROR, ex.getStatus());
        verify(redisUtil, never()).delete(anyString());
        verify(authMapper, never()).insert(any(UserAuthDto.class));
    }

    @Test
    @DisplayName("校验验证码：匹配则消费验证码并完成注册（落库 + 授默认角色 + 建用户信息）")
    void verifyCode_shouldRegisterUser_whenCodeMatches() {
        when(redisUtil.hasKey(CAPTCHA_KEY)).thenReturn(true);
        when(redisUtil.get(eq(CAPTCHA_KEY), ArgumentMatchers.<TypeReference<Integer>>any())).thenReturn(700001);
        // 模拟自增主键回填
        when(authMapper.insert(any(UserAuthDto.class))).thenAnswer(invocation -> {
            invocation.<UserAuthDto>getArgument(0).setId(NEW_USER_ID);
            return 1;
        });

        authService.verifyCode(new VerifyCodeDto(EMAIL, RAW_PASSWORD, 700001));

        verify(redisUtil, times(1)).delete(CAPTCHA_KEY);

        ArgumentCaptor<UserAuthDto> userCaptor = ArgumentCaptor.forClass(UserAuthDto.class);
        verify(authMapper, times(1)).insert(userCaptor.capture());
        UserAuthDto inserted = userCaptor.getValue();
        assertEquals(EMAIL, inserted.getEmail());
        assertNotNull(inserted.getPassword());
        assertTrue(new BCryptPasswordEncoder().matches(RAW_PASSWORD, inserted.getPassword()),
                "密码应以 BCrypt 密文入库，不能存明文");

        verify(authMapper, times(1)).insertUserRole(NEW_USER_ID);
        verify(userFeignClient, times(1)).createUser(new AuthUserDto(NEW_USER_ID, EMAIL));
    }

    // ==================== logout ====================

    @Test
    @DisplayName("登出：清除该用户的在线状态缓存")
    void logout_shouldDeleteOnlineKey() {
        when(jwtUtil.getUserDetailFromJWT(REFRESH_TOKEN))
                .thenReturn(UserDetailDto.builder().userId(USER_ID).build());

        authService.logout(REFRESH_TOKEN);

        verify(redisUtil, times(1)).delete(CacheKeyUtil.buildCacheKey(CacheKey.USER_ONLINE, USER_ID));
    }

    @Test
    @DisplayName("登出：refresh token 无法解析则抛 LOGOUT_FAILED，不裸抛 RuntimeException")
    void logout_shouldThrowLogoutFailed_whenRefreshTokenInvalid() {
        when(jwtUtil.getUserDetailFromJWT("bad-token")).thenThrow(new RuntimeException("Token解析错误"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.logout("bad-token"));

        assertEquals(AuthApiStatus.LOGOUT_FAILED, ex.getStatus());
        verify(redisUtil, never()).delete(anyString());
    }

    // ==================== fixtures ====================

    private static CustomUserDetails userDetails() {
        return new CustomUserDetails(USER_ID, List.of("USER"), List.of("post:read"), EMAIL, ENCODED_PASSWORD);
    }

    private static UserDetailDto details() {
        return UserDetailDto.builder()
                .userId(USER_ID)
                .roles(List.of("USER"))
                .authorities(List.of("post:read"))
                .build();
    }

    private void givenLockAcquired() throws InterruptedException {
        when(redissonClient.getLock(CAPTCHA_LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }
}
