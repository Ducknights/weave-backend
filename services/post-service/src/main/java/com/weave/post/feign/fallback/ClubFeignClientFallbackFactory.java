package com.weave.post.feign.fallback;

import com.weave.model.model.dto.ClubBriefDto;
import com.weave.post.feign.ClubFeignClient;
import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Log4j2
@Component
public class ClubFeignClientFallbackFactory implements FallbackFactory<ClubFeignClient> {

    @Override
    public ClubFeignClient create(Throwable cause) {
        log.error("ClubFeignClient 熔断: {}", cause.getMessage());
        return new ClubFeignClient() {
            @Override
            public Map<Long, ClubBriefDto> getClubInfosByIds(Set<Long> clubIds) {
                log.warn("ClubFeignClient.getClubInfosByIds 降级: ids.size={}", clubIds != null ? clubIds.size() : 0);
                return Collections.emptyMap();
            }
        };
    }
}
