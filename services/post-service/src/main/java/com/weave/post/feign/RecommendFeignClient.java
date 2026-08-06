package com.weave.post.feign;

import com.weave.post.feign.fallback.RecommendFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "recommend-service", fallbackFactory = RecommendFeignClientFallbackFactory.class)
public interface RecommendFeignClient {

    @GetMapping("/api/recommend/post")
    List<Long> getRecommendations(@RequestParam Long userId, @RequestParam int limit);
}
