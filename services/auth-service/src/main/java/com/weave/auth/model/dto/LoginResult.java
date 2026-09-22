package com.weave.auth.model.dto;

import com.weave.model.model.dto.UserBriefDto;

/**
 * 登录结果，包含响应体和用于设置 cookie 的刷新令牌
 */
public record LoginResult(
        LoginResDto loginResDto,
        String refreshToken) {
}
