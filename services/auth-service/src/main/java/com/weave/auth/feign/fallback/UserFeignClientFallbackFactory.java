package com.weave.auth.feign.fallback;

import com.weave.auth.exception.BusinessException;
import com.weave.auth.feign.UserFeignClient;
import com.weave.auth.model.dto.UserDto;
import com.weave.auth.model.enums.AuthApiStatus;
import com.weave.model.model.dto.AuthUserDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserFeignClientFallbackFactory implements FallbackFactory<UserFeignClient> {

    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("UserFeignClient 熔断: {}", cause.getMessage());
        return new UserFeignClient() {
            @Override
            public void createUser(AuthUserDto user) {
                log.error("调用user-service创建用户失败，已熔断");
                throw new BusinessException(AuthApiStatus.REGISTER_FAILED);
            }

            @Override
            public UserDto getUserBriefById(Long id) {
                log.error("调用user-service获取用户信息失败，已熔断，用户ID: {}", id);
                return new UserDto(id, "未知用户", null, null);
            }
        };
    }
}
