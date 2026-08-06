package com.weave.recommend.feign;


import com.weave.model.model.dto.PostDetailVo;
import com.weave.model.model.ApiResult;
import com.weave.recommend.feign.fallback.PostFeignFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "post-service", fallbackFactory = PostFeignFallbackFactory.class)
public interface PostFeign {
    /**
     * 根据ID列表批量获取帖子
     *
     * @param ids 帖子ID列表
     * @return 帖子ID到帖子详情的映射
     */
    @PostMapping("/batch")
    ResponseEntity<ApiResult<Map<Long, PostDetailVo>>> getPostsByIds(@RequestBody List<Long> ids);
}
