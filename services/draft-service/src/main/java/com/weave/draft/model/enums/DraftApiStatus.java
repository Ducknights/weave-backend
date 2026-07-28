package com.weave.draft.model.enums;

import lombok.Getter;
import com.weave.model.model.ApiResult;
import com.weave.model.model.ApiStatus;

/**
 * 草稿服务 API 状态枚举
 */
@Getter
public enum DraftApiStatus implements ApiStatus {
    // 成功状态
    SUCCESS(200, "成功"),
    SAVE_DRAFT_SUCCESS(200, "草稿保存成功"),
    UPDATE_SUCCESS(200, "更新成功"),
    DELETE_SUCCESS(204, "删除成功"),
    SUBMIT_SUCCESS(200, "提交审核成功"),
    APPROVE_SUCCESS(200, "审核通过成功"),
    REJECT_SUCCESS(200, "审核驳回成功"),

    // 参数错误
    INVALID_PARAM(400, "参数无效"),
    EMPTY_TITLE(400, "草稿标题不能为空"),

    // 权限错误
    UNAUTHORIZED(401, "未授权"),
    PERMISSION_DENIED(403, "无权操作"),

    // 资源不存在
    DRAFT_NOT_FOUND(404, "草稿不存在"),

    // 业务逻辑错误
    DRAFT_ALREADY_EXISTS(409, "每个用户只能拥有一条草稿"),

    // 系统错误
    SYSTEM_ERROR(500, "系统错误");

    private final int code;
    private final String msg;

    DraftApiStatus(int code, String msg) {
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
