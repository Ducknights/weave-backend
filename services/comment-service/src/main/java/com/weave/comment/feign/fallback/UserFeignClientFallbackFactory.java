package com.weave.comment.feign.fallback;

import com.weave.comment.feign.UserFeignClient;
import com.weave.model.model.dto.UserBriefDto;
import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Log4j2
@Component
public class UserFeignClientFallbackFactory implements FallbackFactory<UserFeignClient> {

    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("UserFeignClient 熔断: {}", cause.getMessage());
        return new UserFeignClient() {
            @Override
            public Map<Long, UserBriefDto> getUserBriefInfosByIds(Set<Long> ids) {
                log.warn("UserFeignClient.getUserBriefInfosByIds 降级: ids.size={}", ids != null ? ids.size() : 0);
                return Collections.emptyMap();
            }

            @Override
            public Set<Long> getMutedAndBlockedTargetIds() {
                log.warn("UserFeignClient.getMutedAndBlockedTargetIds 降级");
                return Collections.emptySet();
            }
        };
    }
}
