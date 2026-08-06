package com.weave.post.feign;

import com.weave.model.model.dto.UserBriefDto;
import com.weave.post.feign.fallback.UserFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.Set;

@FeignClient(name = "user-service", fallbackFactory = UserFeignClientFallbackFactory.class)
public interface UserFeignClient {

    @PostMapping("/api/user/info/batch")
    Map<Long, UserBriefDto> getUserInfosByIds(@RequestBody Set<Long> ids);

    @GetMapping("/api/user/{id}/loadCache")
    void loadCacheLikeAndCollect(@PathVariable Long id);
}
