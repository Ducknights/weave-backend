package com.weave.recommend.feign.fallback;

import com.weave.model.model.ApiResult;
import com.weave.model.model.dto.PostDetailVo;
import com.weave.recommend.feign.PostFeign;
import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Log4j2
@Component
public class PostFeignFallbackFactory implements FallbackFactory<PostFeign> {

    @Override
    public PostFeign create(Throwable cause) {
        log.error("PostFeign 熔断: {}", cause.getMessage());
        return new PostFeign() {
            @Override
            public ResponseEntity<ApiResult<Map<Long, PostDetailVo>>> getPostsByIds(List<Long> ids) {
                log.warn("PostFeign.getPostsByIds 降级: ids.size={}", ids != null ? ids.size() : 0);
                return ResponseEntity.ok(new ApiResult<>(500, "帖子服务暂不可用", null));
            }
        };
    }
}
