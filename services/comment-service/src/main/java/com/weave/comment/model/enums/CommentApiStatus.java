package com.weave.comment.model.enums;

import lombok.Getter;
import com.weave.model.model.ApiResult;
import com.weave.model.model.ApiStatus;

@Getter
public enum CommentApiStatus implements ApiStatus {
    
    // 成功状态
    ADD_SUCCESS(201, "评论成功"),
    DELETED_SUCCESS(204, "删除成功"),
    GET_SUCCESS(200, "查询成功"),
    LIKE_SUCCESS(200, "点赞成功"),
    UNLIKE_SUCCESS(200, "取消点赞成功"),
    
    // 参数错误
    INVALID_PARAM(400, "参数无效"),
    MISSING_POST_ID(400, "缺少帖子ID"),
    MISSING_USER_ID(400, "缺少用户ID"),
    MISSING_USER_NAME(400, "缺少用户名"),
    EMPTY_CONTENT(400, "评论内容不能为空"),
    CONTENT_TOO_LONG(400, "评论内容不能超过200字符"),
    INVALID_PARENT_ID(400, "无效的父评论ID格式"),
    
    // 权限错误
    UNAUTHORIZED(403, "无权操作"),
    NOT_COMMENT_AUTHOR_OR_ADMIN(403, "无权删除此评论"),
    
    // 资源不存在
    COMMENT_NOT_FOUND(404, "评论不存在"),
    USER_NOT_FOUND(404, "用户不存在"),
    
    // 业务逻辑错误
    DUPLICATE_COMMENT(409, "重复评论"),
    DUPLICATE_LIKE(409, "重复点赞"),
    DELETED_COMMENT_CANNOT_EDIT(400, "已删除的评论无法编辑"),
    
    // 系统错误
    SYSTEM_ERROR(500, "系统错误"),
    COMMENT_FAILED(500, "评论失败"),
    GET_COMMENT_FAILED(500, "获取评论失败"),
    UPDATE_COMMENT_FAILED(500, "编辑失败"),
    DELETE_COMMENT_FAILED(500, "删除失败"),
    LIKE_FAILED(500, "点赞操作失败"),
    GET_REPLIES_FAILED(500, "获取回复失败"),
    GET_USER_COMMENTS_FAILED(500, "获取用户评论失败"),
    GET_RESOURCE_COMMENTS_FAILED(500, "获取资源评论失败");

    private final int code;
    private final String msg;

    CommentApiStatus(int code, String msg) {
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
