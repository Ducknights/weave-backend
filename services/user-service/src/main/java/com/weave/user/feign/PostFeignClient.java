package com.weave.user.feign;

import com.weave.model.model.dto.PostDetailVo;
import com.weave.model.model.ApiResult;
import com.weave.user.feign.fallback.PostFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Set;

/**
 * 帖子服务远程调用接口
 */
@FeignClient(name = "post-service", path = "/api/post", fallbackFactory = PostFeignClientFallbackFactory.class)
public interface PostFeignClient {

    /**
     * 根据用户ID获取该用户的帖子列表
     * @param userId 用户ID
     * @return 帖子列表
     */
    @GetMapping("/user/{userId}")
    ResponseEntity<ApiResult<List<PostDetailVo>>> getPostsByUser(@PathVariable Long userId);

    /**
     * 根据ID列表批量获取帖子
     * @param postIds 帖子ID列表
     * @return 帖子列表
     */
    @PostMapping("/batch")
    List<PostDetailVo> getPostsByIds(@RequestBody Set<Long> postIds);
}
