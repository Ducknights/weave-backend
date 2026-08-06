package com.weave.chat.feign.fallback;

import com.weave.chat.feign.UserInfoFeign;
import com.weave.model.model.dto.UserBriefDto;
import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Log4j2
@Component
public class UserInfoFeignFallbackFactory implements FallbackFactory<UserInfoFeign> {

    @Override
    public UserInfoFeign create(Throwable cause) {
        log.error("UserInfoFeign 熔断: {}", cause.getMessage());
        return new UserInfoFeign() {
            @Override
            public Map<Long, UserBriefDto> getUserInfosByIds(Set<Long> userIds) {
                log.warn("UserInfoFeign.getUserInfosByIds 降级: ids.size={}", userIds != null ? userIds.size() : 0);
                return Collections.emptyMap();
            }
        };
    }
}
