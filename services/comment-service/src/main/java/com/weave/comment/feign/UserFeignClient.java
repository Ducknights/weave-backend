package com.weave.comment.feign;

import com.weave.comment.feign.fallback.UserFeignClientFallbackFactory;
import com.weave.model.model.dto.UserBriefDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.Set;

/**
 * 用户信息 Feign 客户端
 * 用于调用 user-service 获取用户信息
 */
@FeignClient(name = "user-service", fallbackFactory = UserFeignClientFallbackFactory.class)
public interface UserFeignClient {

    /**
     * 批量获取用户信息
     */
    @PostMapping("/api/user/info/batch")
    Map<Long, UserBriefDto> getUserBriefInfosByIds(@RequestBody Set<Long> Ids);

    /**
     * 获取屏蔽+拉黑的目标用户ID集合，用于评论过滤
     */
    @PostMapping("/api/user/relation/filter-ids")
    Set<Long> getMutedAndBlockedTargetIds();
}
