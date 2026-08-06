package com.weave.auth.model.dto;

import com.weave.model.model.dto.UserBriefDto;

/**
 * API响应数据传输对象(DTO)类
 * 用于封装API接口的返回数据，包含令牌和用户信息
 */
public record LoginResDto(
        TokenDto token,
        UserBriefDto user) {
}