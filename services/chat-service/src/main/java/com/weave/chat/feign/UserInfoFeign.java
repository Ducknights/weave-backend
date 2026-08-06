package com.weave.chat.feign;

import com.weave.chat.feign.fallback.UserInfoFeignFallbackFactory;
import com.weave.model.model.dto.UserBriefDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.Set;

@FeignClient(name = "user-service", fallbackFactory = UserInfoFeignFallbackFactory.class)
public interface UserInfoFeign {
    /**
     * 批量获取用户信息
     * @param userIds 用户ID集合
     * @return 用户信息Map，key为用户ID，value为用户信息
     */
    @PostMapping("/api/user/info/batch")
    Map<Long, UserBriefDto> getUserInfosByIds(@RequestBody Set<Long> userIds);
}
