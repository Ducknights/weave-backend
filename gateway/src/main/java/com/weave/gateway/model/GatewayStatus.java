package com.weave.gateway.model;

import lombok.Getter;
import com.weave.model.model.ApiResult;
import com.weave.model.model.ApiStatus;

@Getter
public enum GatewayStatus implements ApiStatus {

    NO_TOKEN(401, "用户未登录，请登录后重试"),
    TOKEN_INVALID(401, "登录信息过期，请重新登录");
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试");

    private final int code;
    private final String msg;

    GatewayStatus(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public ApiResult<?> response() {
        return new ApiResult<>(code, msg, null);
    }

    public <T> ApiResult<T> response(T data) {
        return new ApiResult<>(code, msg, data);
    }
}
