package com.weave.auth.feign.fallback;

import com.weave.auth.exception.BusinessException;
import com.weave.auth.feign.UserFeignClient;
import com.weave.auth.model.enums.AuthApiStatus;
import lombok.extern.slf4j.Slf4j;
import com.weave.model.model.dto.AuthUserDto;
import com.weave.auth.model.dto.UserDto;
import org.springframework.stereotype.Component;

/**
 * UserFeignClient的熔断器实现类
 */
@Component
@Slf4j
public class UserFeignClientFallback implements UserFeignClient {

    @Override
    public void createUser(AuthUserDto user) {
        log.error("调用user-service创建用户失败，已熔断");
        throw new BusinessException(AuthApiStatus.REGISTER_FAILED);
    }


    @Override
    public UserDto getUserBriefById(Long id) {
        log.error("调用user-service获取用户信息失败，已熔断，用户ID: {}", id);
        // 返回默认的用户信息或抛出异常
        return new UserDto(id, "未知用户", null, null);
    }
}