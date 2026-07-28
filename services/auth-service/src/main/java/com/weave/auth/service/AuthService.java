package com.weave.auth.service;


import com.weave.auth.exception.BusinessException;
import com.weave.auth.mapper.AuthMapper;
import com.weave.auth.model.dto.*;
import com.weave.auth.model.enums.AuthApiStatus;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.weave.auth.feign.UserFeignClient;
import com.weave.auth.model.dto.CustomUserDetails;
import com.weave.redis.constant.CacheKey;
import com.weave.model.model.dto.UserBriefDto;
import com.weave.rabbitmq.util.MQUtil;
import com.weave.util.JwtUtil;
import com.weave.redis.util.RedisUtil;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
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

@Slf4j
@Service
@Transactional
public class AuthService {
    @Resource
    private AuthenticationManager authenticationManager;
    @Resource
    private SecurityUserDetailsService service;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private AuthMapper authMapper;
    @Resource
    private UserFeignClient userFeignClient;
    @Resource
    private MQUtil mqUtil;
    @Resource
    private RedisUtil redisUtil;

    private static final int ACCESS_TOKEN_EXPIRE_TIME = 1000 * 60 * 60 * 2; // 2小时 = 1000 * 60 * 60 * 2 毫秒
    private static final int REFRESH_TOKEN_EXPIRE_TIME = 1000 * 60 * 60 * 24 * 7; // 7天 = 1000 * 60 * 60 * 24 * 7 毫秒
    private static final Duration USER_AUTHORITY_CACHE_TTL = Duration.ofMinutes(130); // 缓存用户权限过期时间: 130分钟

    public ApiResponseDto login(ApiRequestDto apiRequestDto) {
        ApiResponseDto apiResponseDto = null;
        try {
            // 使用Spring Security进行认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            apiRequestDto.email(),
                            apiRequestDto.password()
                    )
            );
            if (authentication.isAuthenticated()) {
                // 设置认证上下文
                SecurityContextHolder.getContext().setAuthentication(authentication);
                // 获取用户ID
                Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
                // 生成Redis键
                String permissionsKey = CacheKey.buildCacheKey(CacheKey.USER_AUTHORITY, userId);
                // 生成JWT令牌
                String access_token = JwtUtil.generateJwtToken(permissionsKey, ACCESS_TOKEN_EXPIRE_TIME);
                String refresh_token = JwtUtil.generateJwtToken(permissionsKey, REFRESH_TOKEN_EXPIRE_TIME);
                // 写入用户标识信息到redis
                redisUtil.set(permissionsKey, authentication.getPrincipal(), USER_AUTHORITY_CACHE_TTL);
                // 构造返回DTO
                TokenDto tokenDto = new TokenDto(access_token, ACCESS_TOKEN_EXPIRE_TIME, refresh_token, REFRESH_TOKEN_EXPIRE_TIME);
                // 获取用户信息
                UserBriefDto userBriefDto = userFeignClient.getUserBriefById(userId);
                // 获取用户角色
                List<String> roleNames = ((CustomUserDetails) authentication.getPrincipal()).getRoles();
                UserDto userDto = new UserDto(userId, userBriefDto.getName(), userBriefDto.getAvatar(), roleNames);
                // 构建响应DTO
                apiResponseDto = new ApiResponseDto(tokenDto, userDto);
            }
        } catch (Exception e) {
            log.error("登录失败: {}", e.getMessage(), e);
            throw new BusinessException(AuthApiStatus.LOGIN_FAILED);
        }
        return apiResponseDto;
    }

    public void sendCode(ApiRequestDto apiRequestDto) {
        String email = apiRequestDto.email();
        // 验证邮箱是否已存在
        if (authMapper.selectUserByEmail(email) != null){
            throw new BusinessException(AuthApiStatus.EMAIL_ALREADY_REGISTERED);
        }
        // 发送验证码
        String lock = CacheKey.buildCacheKey("lock" + CacheKey.CAPTCHA, email);
        log.info("发送验证码到: {}", email);
        if (Boolean.TRUE.equals(redisUtil.hasKey(lock))){
            throw new BusinessException(AuthApiStatus.CODE_ALREADY_SENT);
        }
        try{
            // 发送验证码到验证码队列
            mqUtil.sendCaptchaCode(email);
        }catch (Exception e){
            throw new BusinessException(AuthApiStatus.CODE_SEND_FAILED);
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

    @Caching(evict = {
            @CacheEvict(value = CacheKey.USER_AUTHORITY, key = "#userId"),
            @CacheEvict(value = CacheKey.USER_ONLINE,key = "#userId")})
    public void logout(Long userId){
        try {
            SecurityContextHolder.clearContext();
            log.info("User logged out: {}", userId);
        } catch (Exception e) {
            throw new BusinessException(AuthApiStatus.LOGOUT_FAILED);
        }
    }

    public TokenDto getNewSuccessToken(Long userId) {
        try {
            // 1. 生成JWT令牌
            String subject = CacheKey.buildCacheKey(CacheKey.USER_AUTHORITY, userId);
            String access_token = JwtUtil.generateJwtToken(subject, ACCESS_TOKEN_EXPIRE_TIME);
            // 2. 缓存用户权限
            cacheUserAuthorities(userId);
            // 3. 构造返回DTO
            return new TokenDto(access_token, ACCESS_TOKEN_EXPIRE_TIME,null , null);
        } catch (Exception e) {
            throw new BusinessException(AuthApiStatus.TOKEN_GENERATE_FAILED);
        }
    }

    public TokenDto getNewRefreshToken(Long userId){
        try {
            // 1. 生成JWT令牌
            String subject = CacheKey.buildCacheKey(CacheKey.USER_AUTHORITY, userId);
            String refresh_token = JwtUtil.generateJwtToken(subject, REFRESH_TOKEN_EXPIRE_TIME);
            // 2. 重新缓存用户权限信息
            cacheUserAuthorities(userId);
            // 3. 构造返回DTO
            return new TokenDto(null,null , refresh_token, REFRESH_TOKEN_EXPIRE_TIME);
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
        redisUtil.set(cacheKey, userDetails, USER_AUTHORITY_CACHE_TTL);
        log.info("已刷新用户权限缓存: userId={}", userId);
    }
}
