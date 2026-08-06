package com.weave.search.feign.fallback;

import com.weave.model.model.dto.PostDetailVo;
import com.weave.search.feign.PostFeignClient;
import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Log4j2
@Component
public class PostFeignClientFallbackFactory implements FallbackFactory<PostFeignClient> {

    @Override
    public PostFeignClient create(Throwable cause) {
        log.error("PostFeignClient 熔断: {}", cause.getMessage());
        return new PostFeignClient() {
            @Override
            public List<PostDetailVo> getPostsByIds(List<Long> postIds) {
                log.warn("PostFeignClient.getPostsByIds 降级: ids.size={}", postIds != null ? postIds.size() : 0);
                return Collections.emptyList();
            }
        };
    }
}
