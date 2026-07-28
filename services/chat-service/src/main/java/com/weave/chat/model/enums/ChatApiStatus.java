package com.weave.chat.model.enums;

import lombok.Getter;
import com.weave.model.model.ApiResult;
import com.weave.model.model.ApiStatus;

@Getter
public enum ChatApiStatus implements ApiStatus {

    GET_CONVERSATIONS_SUCCESS(200, "获取会话列表成功"),
    GET_MESSAGES_SUCCESS(200, "获取消息成功"),

    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "会话不存在"),

    INTERNAL_SERVER_ERROR(500, "服务器内部错误");

    private final int code;
    private final String msg;

    ChatApiStatus(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public ApiResult<Void> response() {
        return response(null);
    }

    public <T> ApiResult<T> response(T data) {
        return new ApiResult<>(code, msg, data);
    }
}
