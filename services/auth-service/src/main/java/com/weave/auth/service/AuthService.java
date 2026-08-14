package com.weave.auth.service;


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


    public LoginResDto login(ApiRequestDto apiRequestDto) {
        LoginResDto loginResDto = null;
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
                // 生成JWT令牌
                String access_token = JwtUtil.generateJwtToken(permissionsKey, ACCESS_TOKEN_TTL_MILLIS);
                String refresh_token = JwtUtil.generateJwtToken(permissionsKey,REFRESH_TOKEN_TTL_MILLIS);
                // 写入用户标识信息到redis
                redisUtil.set(permissionsKey, authentication.getPrincipal(), Duration.ofMinutes(USER_AUTHORITY_TTL_MINUTES));
                // 构造返回DTO
                TokenDto tokenDto = new TokenDto(access_token, ACCESS_TOKEN_TTL_MILLIS, refresh_token, REFRESH_TOKEN_TTL_MILLIS);
                // 获取用户信息
                UserBriefDto userBriefDto = userFeignClient.getUserBriefById(userId);
                // 获取用户角色
                List<String> roleNames = ((CustomUserDetails) authentication.getPrincipal()).getRoles();
                UserDto userDto = new UserDto(userId, userBriefDto.getName(), userBriefDto.getAvatar(), roleNames);
                // 构建响应DTO
                loginResDto = new LoginResDto(tokenDto, userDto);
            }
        } catch (Exception e) {
            log.error("登录失败: {}", e.getMessage());
            throw new BusinessException(AuthApiStatus.LOGIN_FAILED);
        }
        return loginResDto;
    }

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

    public void register(VerifyCodeDto dto) {
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

    public TokenDto getNewSuccessToken(Long userId) {
        try {
            // 1. 生成JWT令牌
            String subject = CacheKey.buildCacheKey(CacheKey.USER_AUTHORITY, userId);
            String access_token = JwtUtil.generateJwtToken(subject, ACCESS_TOKEN_TTL_MILLIS);
            // 2. 缓存用户权限
            cacheUserAuthorities(userId);
            // 3. 构造返回DTO
            return new TokenDto(access_token, ACCESS_TOKEN_TTL_MILLIS,null , null);
        } catch (Exception e) {
            throw new BusinessException(AuthApiStatus.TOKEN_GENERATE_FAILED);
        }
    }

    public TokenDto getNewRefreshToken(Long userId){
        try {
            // 1. 生成JWT令牌
            String subject = CacheKey.buildCacheKey(CacheKey.USER_AUTHORITY, userId);
            String refresh_token = JwtUtil.generateJwtToken(subject, REFRESH_TOKEN_TTL_MILLIS);
            // 2. 重新缓存用户权限信息
            cacheUserAuthorities(userId);
            // 3. 构造返回DTO
            return new TokenDto(null,null , refresh_token, REFRESH_TOKEN_TTL_MILLIS);
        } catch (Exception e) {
            throw new BusinessException(AuthApiStatus.TOKEN_GENERATE_FAILED);
        }
    }

    private void cacheUserAuthorities(Long userId) {
        // 1. 从数据库重新加载用户角色和权限
        CustomUserDetails userDetails = authMapper.selectUserDetailsById(userId);
        if (userDetails == null) {
            throw new BusinessException(AuthApiStatus.USER_NOT_FOUND);
        }
        // 2. 缓存到 Redis
        String cacheKey = CacheKey.buildCacheKey(CacheKey.USER_AUTHORITY, userId);
        redisUtil.set(cacheKey, userDetails, Duration.ofMinutes(USER_AUTHORITY_TTL_MINUTES));
        log.info("已刷新用户权限缓存: userId={}", userId);
    }
}
