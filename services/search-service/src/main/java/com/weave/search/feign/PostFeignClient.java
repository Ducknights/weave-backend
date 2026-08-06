package com.weave.search.feign;

import com.weave.model.model.dto.PostDetailVo;
import com.weave.search.feign.fallback.PostFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 帖子服务远程调用接口
 */
@FeignClient(name = "post-service", path = "/api/post", fallbackFactory = PostFeignClientFallbackFactory.class)
public interface PostFeignClient {

    /**
     * 根据ID列表批量获取帖子
     *
     * @param postIds 帖子ID列表
     * @return 帖子ID到帖子详情的映射
     */
    @PostMapping("/batch")
    List<PostDetailVo> getPostsByIds(@RequestBody List<Long> postIds);
}