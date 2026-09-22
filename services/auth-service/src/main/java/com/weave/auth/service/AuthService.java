package com.weave.auth.service;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weave.auth.exception.BusinessException;
import com.weave.auth.mapper.AuthMapper;
import com.weave.auth.model.dto.*;
import com.weave.auth.model.enums.AuthApiStatus;
import com.weave.model.model.dto.AuthUserDto;
import com.weave.model.model.dto.UserDetailDto;
import com.weave.model.util.CacheKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import com.weave.auth.feign.UserFeignClient;
import com.weave.redis.constant.CacheKey;
import com.weave.model.model.dto.UserBriefDto;
import com.weave.rabbitmq.util.MQUtil;
import com.weave.model.util.JwtUtil;
import com.weave.redis.util.RedisUtil;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.weave.auth.model.constans.CaCheTTL.*;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AuthMapper authMapper;
    private final UserFeignClient userFeignClient;
    private final MQUtil mqUtil;
    private final ObjectMapper objectMapper;
    private final RedisUtil redisUtil;
    private final JwtUtil jwtUtil;
    private final RedissonClient redissonClient;

    /**
     * 登录
     */
    public LoginResult login(ApiRequestDto apiRequestDto) {
        try {
            // 查询用户并校验密码
            CustomUserDetails customUserDetails = authMapper.selectUserDetailsByEmail(apiRequestDto.email());
            if (customUserDetails == null
                    || !passwordEncoder.matches(apiRequestDto.password(), customUserDetails.getPassword())) {
                throw new BusinessException(AuthApiStatus.LOGIN_FAILED);
            }

            // 构建JWT载荷
            UserDetailDto userDetailDto = UserDetailDto.builder()
                    .userId(customUserDetails.getUserId())
                    .roles(customUserDetails.getRoles())
                    .authorities(customUserDetails.getAuthorities())
                    .build();
            log.info("用户ID: {}登录成功", userDetailDto);
            // 签发令牌
            TokenDto tokenDto = getAccessToken(userDetailDto);
            String refreshToken = getRefreshToken(userDetailDto);
            // 获取用户基本信息
            UserBriefDto userBriefDto = userFeignClient.getUserBriefById(customUserDetails.getUserId());
            UserDto userDto = new UserDto(userBriefDto.getId(), userBriefDto.getName(), userBriefDto.getAvatar(), customUserDetails.getRoles());
            return new LoginResult(new LoginResDto(tokenDto, userDto), refreshToken);
        } catch (BusinessException e) {
            log.warn("登录失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("登录失败", e);
            throw new BusinessException(AuthApiStatus.SYSTEM_ERROR);
        }
    }

    /**
     * 获取访问令牌
     */
    @SneakyThrows
    public TokenDto getAccessToken(UserDetailDto userDetails) {
        Map<String, Object> claims = objectMapper.convertValue(userDetails, new TypeReference<>() {});
        log.info("claims = {}", claims);
        // 生成JWT访问令牌
        String access_token = jwtUtil.generateJwtToken(userDetails.getUserId(), claims, ACCESS_TOKEN_TTL_MILLIS);
        // 构造返回DTO
        return new TokenDto(access_token, ACCESS_TOKEN_TTL_MILLIS);
    }

    /**
     * 获取刷新令牌
     */
    @SneakyThrows
    public String getRefreshToken(UserDetailDto userDetails) {
        Map<String, Object> claims = objectMapper.convertValue(userDetails, new TypeReference<>() {});
        // 生成JWT刷新令牌
        return jwtUtil.generateJwtToken(userDetails.getUserId(), claims, REFRESH_TOKEN_TTL_MILLIS);
    }

    /**
     * 获取新访问令牌
     */
    public TokenDto getNewAccessToken(String refreshToken) {
        try {
            // 解析刷新令牌得到用户信息
            UserDetailDto userDetailDto = jwtUtil.getUserDetailFromJWT(refreshToken);
            // 构造返回DTO
            return getAccessToken(userDetailDto);
        }catch (Exception e){
            throw new BusinessException(AuthApiStatus.TOKEN_GENERATE_FAILED);
        }
    }

    /**
     * 获取新刷新令牌
     */
    @SneakyThrows
    public Optional<String> getNewRefreshToken(String refreshToken) {
        try {
            // 如果refresh token 还有足够长的时间有效期，则不进行刷新
            if (jwtUtil.getExpirationFromJWT(refreshToken) > TOKEN_ROTATION_THRESHOLD) {
                return Optional.empty();
            }
            UserDetailDto userDetailDto = jwtUtil.getUserDetailFromJWT(refreshToken);
            return Optional.of(getRefreshToken(userDetailDto));
        } catch (Exception e) {
            throw new BusinessException(AuthApiStatus.TOKEN_GENERATE_FAILED);
        }
    }

    /**
     * 发送验证码
     */
    public void sendCode(ApiRequestDto apiRequestDto) {
        String email = apiRequestDto.email();
        // Redisson 分布式锁：1分钟内不可重试
        String lockKey = CacheKeyUtil.buildLockKey(CacheKey.CAPTCHA, email);
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
        String key = CacheKeyUtil.buildCacheKey(CacheKey.CAPTCHA, dto.email());
        if (Boolean.FALSE.equals(redisUtil.hasKey(key))){
            throw new BusinessException(AuthApiStatus.CODE_EXPIRED);
        }
        Integer code = redisUtil.get(key, new TypeReference<>() {});
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
            UserAuthDto userAuthDto = new UserAuthDto();
            userAuthDto.setEmail(dto.email());
            userAuthDto.setPassword(passwordEncoder.encode(dto.password()));
            // 插入用户信息
            authMapper.insert(userAuthDto);
            // 插入用户角色，默认角色为普通用户
            authMapper.insertUserRole(userAuthDto.getId());
            // 调用用户服务插入用户信息
            userFeignClient.createUser(new AuthUserDto(userAuthDto.getId(), userAuthDto.getEmail()));
        } catch (Exception e) {
            throw new BusinessException(AuthApiStatus.REGISTER_FAILED);
        }
    }

    /**
     * 登出
     */
    @SneakyThrows
    public void logout(String refreshToken){
        try {
            UserDetailDto userDetailDto = jwtUtil.getUserDetailFromJWT(refreshToken);
            redisUtil.delete(CacheKeyUtil.buildCacheKey(CacheKey.USER_ONLINE, userDetailDto.getUserId()));
            log.info("用户ID: {}已登出", userDetailDto.getUserId());
        } catch (Exception e) {
            throw new BusinessException(AuthApiStatus.LOGOUT_FAILED);
        }
    }
}
