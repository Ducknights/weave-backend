package com.weave.post.model.enums;

import lombok.Getter;
import com.weave.model.model.ApiResult;
import com.weave.model.model.ApiStatus;

/**
 * 帖子操作类型枚举 - 封装所有帖子操作的配置信息
 */
@Getter
public enum PostApiStatus implements ApiStatus {
    // 成功状态
    SUCCESS(200, "成功"),
    CREATE_SUCCESS(201, "创建成功"),
    UPDATE_SUCCESS(200, "更新成功"),
    DELETE_SUCCESS(204, "删除成功"),
    LIKE_SUCCESS(200, "点赞成功"),
    UNLIKE_SUCCESS(200, "取消点赞成功"),
    FAVORITE_SUCCESS(200, "收藏成功"),
    UNFAVORITE_SUCCESS(200, "取消收藏成功"),
    SAVE_DRAFT_SUCCESS(200, "草稿保存成功"),
    SUBMIT_SUCCESS(200, "提交审核成功"),
    APPROVE_SUCCESS(200, "审核通过成功"),
    REJECT_SUCCESS(200, "审核驳回成功"),
    
    // 参数错误
    INVALID_PARAM(400, "参数无效"),
    MISSING_POST_ID(400, "缺少帖子ID"),
    MISSING_USER_ID(400, "缺少用户ID"),
    EMPTY_TITLE(400, "帖子标题不能为空"),
    EMPTY_CONTENT(400, "帖子内容不能为空"),
    TITLE_TOO_LONG(400, "帖子标题不能超过200字符"),
    CONTENT_TOO_LONG(400, "帖子内容不能超过10000字符"),
    
    // 权限错误
    UNAUTHORIZED(401, "未授权"),
    PERMISSION_DENIED(403, "无权操作"),
    
    // 资源不存在
    POST_NOT_FOUND(404, "帖子不存在"),
    USER_NOT_FOUND(404, "用户不存在"),
    
    // 业务逻辑错误
    ALREADY_EXISTS(409, "已存在"),
    DELETED_POST_CANNOT_EDIT(400, "已删除的帖子无法编辑"),
    CANNOT_LIKE_OWN_POST(400, "不能点赞自己的帖子"),
    CANNOT_FAVORITE_OWN_POST(400, "不能收藏自己的帖子"),
    
    // 系统错误
    SYSTEM_ERROR(500, "系统错误"),
    CREATE_FAILED(500, "创建失败"),
    UPDATE_FAILED(500, "更新失败"),
    DELETE_FAILED(500, "删除失败"),
    LIKE_FAILED(500, "点赞操作失败"),
    FAVORITE_FAILED(500, "收藏操作失败"),
    SHARE_FAILED(500, "转发操作失败"),
    GET_POST_FAILED(500, "获取帖子失败"),
    GET_POSTS_FAILED(500, "获取帖子列表失败");

    private final int code;
    private final String msg;

    PostApiStatus(int code, String msg) {
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
