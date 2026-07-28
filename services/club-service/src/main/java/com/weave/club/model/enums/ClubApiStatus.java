package com.weave.club.model.enums;

import lombok.Getter;
import com.weave.model.model.ApiResult;
import com.weave.model.model.ApiStatus;

@Getter
public enum ClubApiStatus implements ApiStatus {
    GET_SUCCESS(200, "请求成功"),
    GET_FAIL(400, "请求失败"),
    GET_NULL(200, "无结果"),
    POST_SUCCESS(201, "创建成功"),
    POST_FAIL(400, "创建失败"),
    PUT_SUCCESS(200, "更新成功"),
    PUT_FAIL(400, "更新失败"),
    DELETE_SUCCESS(200, "删除成功"),
    DELETE_FAIL(400, "删除失败"),
    FORBIDDEN(403, "无权限"),
    INTERNAL_SERVER_ERROR(500, "服务器错误"),
    BAD_REQUEST(400, "请求错误"),
    NOT_FOUND(404, "未找到");

    private final int code;
    private final String msg;
    ClubApiStatus(int code, String msg) {
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
