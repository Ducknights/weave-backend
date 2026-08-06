package com.weave.auth.feign;

import com.weave.model.model.dto.UserBriefDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import com.weave.auth.feign.fallback.UserFeignClientFallback;

import com.weave.model.model.dto.AuthUserDto;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service", fallback = UserFeignClientFallback.class)
public interface UserFeignClient {

    /**
     * 创建用户
     *
     * @param user 用户信息
     */
    @PostMapping("/api/user/info")
    void createUser(@RequestBody AuthUserDto user);

    /**
     * 根据用户ID获取用户信息
     *
     * @param id 用户ID，通过路径变量传递
     * @return 返回UserDto对象，包含用户详细信息
     */
    @GetMapping("/api/user/{id}/info")
    UserBriefDto getUserBriefById(@PathVariable Long id);
}
