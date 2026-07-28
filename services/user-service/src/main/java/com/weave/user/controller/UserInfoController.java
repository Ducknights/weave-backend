package com.weave.user.controller;

import com.weave.user.model.vo.UserInfoVo;
import com.weave.user.service.UserInfoService;
import jakarta.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import com.weave.model.model.dto.AuthUserDto;
import com.weave.model.model.dto.UserBriefDto;
import com.weave.model.model.ApiResult;
import com.weave.user.model.dto.UpdateUserInfoDto;
import com.weave.user.model.entity.UserInfo;
import com.weave.user.model.eunms.UserApiStatus;
import com.weave.security.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@Log4j2
@RestController
@RequestMapping("/api/user")
public class UserInfoController {

    @Resource
    private UserInfoService userInfoService;

    /**
     * 处理用户注册请求的接口方法
     *
     * @param user 包含用户注册信息的AuthUserDto对象，通过请求体传递
     * @return UserInfo 返回创建成功的用户信息对象
     */
    @PostMapping("/info")
    public UserInfo createUser(@RequestBody AuthUserDto user) {
        return userInfoService.createUser(user);
    }

    /**
     * 根据ID获取用户信息的接口方法
     *
     * @param id 用户ID，通过路径变量传递
     * @return UserInfo 返回用户信息对象
     */
    @GetMapping("/{id}/info")
    public UserBriefDto getUserById(@PathVariable Long id) {
        log.info("收到请求{}",id);
        return userInfoService.getUserBriefDtoById(id);
    }

    /**
     * 根据ID获取用户详细信息的接口方法
     *
     * @param id 用户ID，通过路径变量传递
     * @return UserInfoDto 返回用户详细信息对象
     */
    @GetMapping("/{id}/detail")
    public ResponseEntity<ApiResult<UserInfoVo>> getUserDetailById(@PathVariable Long id) {
        UserInfoVo vo = userInfoService.getUserInfoDtoById(id);
        return ResponseEntity.ok().body(UserApiStatus.SUCCESS.response(vo));
    }

    /**
     * 根据用户ID批量获取用户信息
     *
     * @param ids 用户ID集合
     * @return 返回一个Map，键为用户ID，值为对应的用户信息对象
     */
    @PostMapping("/info/batch")
    public Map<Long, UserBriefDto> getUserInfosByIds(@RequestBody Set<Long> ids) {
        return userInfoService.getUserInfosByIds(ids);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/info")
    public UpdateUserInfoDto updateUser(@RequestBody UpdateUserInfoDto user) {
        Long userId = SecurityUtils.getCurrentUserId();
        user.setId(userId);
        return userInfoService.updateUser(user);
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok().body("服务运行正常");
    }
}
