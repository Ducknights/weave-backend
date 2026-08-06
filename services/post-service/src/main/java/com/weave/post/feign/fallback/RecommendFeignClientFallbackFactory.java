package com.weave.post.feign.fallback;

import com.weave.post.feign.RecommendFeignClient;
import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Log4j2
@Component
public class RecommendFeignClientFallbackFactory implements FallbackFactory<RecommendFeignClient> {

    @Override
    public RecommendFeignClient create(Throwable cause) {
        log.error("RecommendFeignClient 熔断: {}", cause.getMessage());
        return new RecommendFeignClient() {
            @Override
            public List<Long> getRecommendations(Long userId, int limit) {
                log.warn("RecommendFeignClient.getRecommendations 降级: userId={}, limit={}", userId, limit);
                return Collections.emptyList();
            }
        };
    }
}
