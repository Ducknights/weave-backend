package com.weave.user.feign.fallback;

import com.weave.model.model.ApiResult;
import com.weave.model.model.dto.PostDetailVo;
import com.weave.user.feign.PostFeignClient;
import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Log4j2
@Component
public class PostFeignClientFallbackFactory implements FallbackFactory<PostFeignClient> {

    @Override
    public PostFeignClient create(Throwable cause) {
        log.error("PostFeignClient 熔断: {}", cause.getMessage());
        return new PostFeignClient() {
            @Override
            public ResponseEntity<ApiResult<List<PostDetailVo>>> getPostsByUser(Long userId) {
                log.warn("PostFeignClient.getPostsByUser 降级: userId={}", userId);
                return ResponseEntity.ok(new ApiResult<>(500, "帖子服务暂不可用", null));
            }

            @Override
            public List<PostDetailVo> getPostsByIds(Set<Long> postIds) {
                log.warn("PostFeignClient.getPostsByIds 降级: ids.size={}", postIds != null ? postIds.size() : 0);
                return Collections.emptyList();
            }
        };
    }
}
