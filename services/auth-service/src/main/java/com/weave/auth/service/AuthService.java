package com.weave.auth.service;


import com.weave.auth.event.UserAuthoritiesRefreshEvent;
import com.weave.auth.exception.BusinessException;
import com.weave.auth.mapper.AuthMapper;
import com.weave.auth.model.dto.*;
import com.weave.auth.model.enums.AuthApiStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.weave.auth.feign.UserFeignClient;
import com.weave.auth.model.dto.CustomUserDetails;
import com.weave.redis.constant.CacheKey;
import com.weave.model.model.dto.UserBriefDto;
import com.weave.rabbitmq.util.MQUtil;
import com.weave.util.JwtUtil;
import com.weave.redis.util.RedisUtil;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.weave.auth.model.constans.CaCheTTL.*;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final SecurityUserDetailsService service;
    private final PasswordEncoder passwordEncoder;
    private final AuthMapper authMapper;
    private final UserFeignClient userFeignClient;
    private final MQUtil mqUtil;
    private final RedisUtil redisUtil;
    private final RedissonClient redissonClient;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 登录
     */
    public UserDto login(ApiRequestDto apiRequestDto) {
        try {
            // 使用Spring Security进行认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            apiRequestDto.email(),
                            apiRequestDto.password()
                    )
            );
            // 生成JWT令牌
            if (authentication.isAuthenticated()) {
                // 设置认证上下文
                SecurityContextHolder.getContext().setAuthentication(authentication);
                // 获取用户ID
                Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
                // 生成Redis键
                String permissionsKey = CacheKey.buildCacheKey(CacheKey.USER_AUTHORITY, userId);
                // 写入用户标识信息到redis
                redisUtil.set(permissionsKey, authentication.getPrincipal(), Duration.ofMinutes(USER_AUTHORITY_TTL_MINUTES));
                // 获取用户信息
                UserBriefDto userBriefDto = userFeignClient.getUserBriefById(userId);
                // 获取用户角色
                List<String> roleNames = ((CustomUserDetails) authentication.getPrincipal()).getRoles();
                return new UserDto(userId, userBriefDto.getName(), userBriefDto.getAvatar(), roleNames);
            }
        } catch (Exception e) {
            log.error("登录失败: {}", e.getMessage());
            throw new BusinessException(AuthApiStatus.LOGIN_FAILED);
        }
        throw new BusinessException(AuthApiStatus.LOGIN_FAILED);
    }

    /**
     * 获取访问令牌
     */
    public TokenDto getAccessToken(Long userId) {
        // 生成Redis键
        String permissionsKey = CacheKey.buildCacheKey(CacheKey.USER_AUTHORITY, userId);
        // 生成JWT令牌
        String access_token = JwtUtil.generateJwtToken(permissionsKey, ACCESS_TOKEN_TTL_MILLIS);
        // 构造返回DTO
        return new TokenDto(access_token, ACCESS_TOKEN_TTL_MILLIS);
    }

    /**
     * 获取刷新令牌
     */
    public String getRefreshToken(Long userId) {
        // 生成Redis键
        String permissionsKey = CacheKey.buildCacheKey(CacheKey.USER_AUTHORITY, userId);
        return JwtUtil.generateJwtToken(permissionsKey,REFRESH_TOKEN_TTL_MILLIS);
    }

    /**
     * 获取新访问令牌
     */
    public TokenDto getNewAccessToken(String refreshToken) {
        try {
            // 生成访问令牌
            Long userId = Long.valueOf(JwtUtil.getUserIdFromJWT(refreshToken));
            TokenDto dto = getAccessToken(userId);
            // 异步刷新用户权限缓存
            eventPublisher.publishEvent(new UserAuthoritiesRefreshEvent(this, userId));
            // 3. 构造返回DTO
            return dto;
        }catch (Exception e){
            throw new BusinessException(AuthApiStatus.TOKEN_GENERATE_FAILED);
        }
    }

    /**
     * 获取新刷新令牌
     */
    public Optional<String> getNewRefreshToken(String refreshToken) {
        if (JwtUtil.getExpirationFromJWT(refreshToken) < TOKEN_ROTATION_THRESHOLD){
            Long userId = Long.valueOf(JwtUtil.getUserIdFromJWT(refreshToken));
            return Optional.of(getRefreshToken(userId));
        }
        return Optional.empty();
    }

    /**
     * 发送验证码
     */
    public void sendCode(ApiRequestDto apiRequestDto) {
        String email = apiRequestDto.email();
        // Redisson 分布式锁：1分钟内不可重试
        String lockKey = CacheKey.buildLockKey(CacheKey.CAPTCHA, email);
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            // 不等待，持有60秒后自动释放
            locked = lock.tryLock(0, 60, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(AuthApiStatus.CODE_ALREADY_SENT);
            }
            // 验证邮箱是否已存在
            if (authMapper.selectUserByEmail(email) != null) {
                throw new BusinessException(AuthApiStatus.EMAIL_ALREADY_REGISTERED);
            }
            // 发送验证码到验证码队列
            log.info("验证码发送到邮箱: {}", email);
            mqUtil.sendCaptchaCode(email);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(AuthApiStatus.CODE_SEND_FAILED);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("发送验证码失败, email={}", email, e);
            throw new BusinessException(AuthApiStatus.CODE_SEND_FAILED);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 验证验证码
     */
    public void verifyCode(VerifyCodeDto dto) {
        // 1. 验证验证码
        String key = CacheKey.buildCacheKey(CacheKey.CAPTCHA, dto.email());
        if (Boolean.FALSE.equals(redisUtil.hasKey(key))){
            throw new BusinessException(AuthApiStatus.CODE_EXPIRED);
        }
        Integer code = redisUtil.get(key, Integer.class);
        if (!dto.code().equals(code)){
            throw new BusinessException(AuthApiStatus.CODE_ERROR);
        }
        // 2. 删除验证码
        redisUtil.delete(key);
        register(dto);
    }

    /**
     * 注册
     */
    private void register(VerifyCodeDto dto) {
        try {
            UserDetails user = User.builder()
                    .username(dto.email())
                    .password(passwordEncoder.encode(dto.password()))
                    .build();
            service.createUser(user);
        }catch (Exception e){
            throw new BusinessException(AuthApiStatus.REGISTER_FAILED);
        }
    }

    /**
     * 登出
     */
    public void logout(Long userId){
        try {
            redisUtil.delete(CacheKey.buildCacheKey(CacheKey.USER_AUTHORITY, userId));
            redisUtil.delete(CacheKey.buildCacheKey(CacheKey.USER_ONLINE, userId));
            SecurityContextHolder.clearContext();
            log.info("用户ID: {}已登出", userId);
        } catch (Exception e) {
            throw new BusinessException(AuthApiStatus.LOGOUT_FAILED);
        }
    }
}
