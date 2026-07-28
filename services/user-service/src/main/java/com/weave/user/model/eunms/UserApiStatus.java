package com.weave.user.model.eunms;

import lombok.Getter;
import com.weave.model.model.ApiResult;
import com.weave.model.model.ApiStatus;

/**
 * 用户服务 API 状态码枚举
 * 统一管理用户服务的所有 API 响应状态
 */
@Getter
public enum UserApiStatus implements ApiStatus {

    // 成功状态 (2xx)
    SUCCESS(200, "成功"),
    GET_SUCCESS(200, "获取成功"),
    CREATE_SUCCESS(201, "创建成功"),
    UPDATE_SUCCESS(200, "更新成功"),
    DELETE_SUCCESS(200, "删除成功"),
    LOGIN_SUCCESS(200, "登录成功"),
    REGISTER_SUCCESS(201, "注册成功"),
    LOGOUT_SUCCESS(200, "退出登录成功"),
    FOLLOW_SUCCESS(200, "关注成功"),
    UNFOLLOW_SUCCESS(200, "取消关注成功"),
    UPDATE_PROFILE_SUCCESS(200, "更新个人资料成功"),
    UPDATE_PASSWORD_SUCCESS(200, "修改密码成功"),
    BIND_EMAIL_SUCCESS(200, "绑定邮箱成功"),
    BIND_PHONE_SUCCESS(200, "绑定手机成功"),

    // 参数错误 (4xx)
    INVALID_PARAM(400, "参数无效"),
    MISSING_USER_ID(400, "缺少用户ID"),
    MISSING_USERNAME(400, "缺少用户名"),
    MISSING_PASSWORD(400, "缺少密码"),
    MISSING_EMAIL(400, "缺少邮箱"),
    MISSING_PHONE(400, "缺少手机号"),
    MISSING_TOKEN(400, "缺少令牌"),
    EMPTY_USERNAME(400, "用户名为空"),
    EMPTY_PASSWORD(400, "密码为空"),
    EMPTY_EMAIL(400, "邮箱为空"),
    EMPTY_NICKNAME(400, "昵称为空"),
    USERNAME_TOO_LONG(400, "用户名不能超过15个字符"),
    PASSWORD_TOO_SHORT(400, "密码不能少于6个字符"),
    PASSWORD_TOO_LONG(400, "密码不能超过20个字符"),
    EMAIL_FORMAT_ERROR(400, "邮箱格式错误"),
    PHONE_FORMAT_ERROR(400, "手机号格式错误"),
    BIRTHDAY_FORMAT_ERROR(400, "生日格式错误"),
    INVALID_BIRTHDAY(400, "生日不能是未来时间"),
    MOTTO_TOO_LONG(400, "个性签名不能超过100个字符"),
    ADDRESS_TOO_LONG(400, "地址不能超过50个字符"),
    AVATAR_FORMAT_ERROR(400, "头像格式错误，仅支持 JPG、PNG、GIF"),
    GENDER_INVALID(400, "性别参数无效"),

    // 认证授权错误 (401/403)
    UNAUTHORIZED(401, "未授权"),
    NOT_LOGGED_IN(401, "未登录"),
    TOKEN_EXPIRED(401, "令牌已过期"),
    TOKEN_INVALID(401, "令牌无效"),
    TOKEN_MALFORMED(401, "令牌格式错误"),
    PASSWORD_ERROR(401, "密码错误"),
    VERIFICATION_CODE_ERROR(401, "验证码错误"),
    VERIFICATION_CODE_EXPIRED(401, "验证码已过期"),
    EMAIL_ALREADY_BOUND(409, "该邮箱已被绑定"),
    PHONE_ALREADY_BOUND(409, "该手机号已被绑定"),

    // 权限错误 (403)
    PERMISSION_DENIED(403, "无权操作"),
    NOT_OWNER(403, "无权操作他人的资源"),
    USER_BANNED(403, "该用户已被禁用"),
    ACTION_NOT_ALLOWED(403, "该操作不被允许"),

    // 资源不存在 (404)
    USER_NOT_FOUND(404, "用户不存在"),
    USERNAME_NOT_FOUND(404, "用户名不存在"),
    EMAIL_NOT_FOUND(404, "邮箱不存在"),
    PHONE_NOT_FOUND(404, "手机号不存在"),
    USER_INFO_NOT_FOUND(404, "用户信息不存在"),
    RELATION_NOT_FOUND(404, "关系不存在"),

    // 资源冲突 (409)
    USERNAME_ALREADY_EXISTS(409, "用户名已存在"),
    EMAIL_ALREADY_EXISTS(409, "邮箱已被注册"),
    PHONE_ALREADY_EXISTS(409, "手机号已被注册"),
    USERNAME_ALREADY_BIND(409, "用户名已绑定"),
    ALREADY_FOLLOWED(409, "已经关注过该用户"),
    NOT_FOLLOWED(409, "尚未关注该用户"),
    CANNOT_FOLLOW_SELF(409, "不能关注自己"),
    CANNOT_UNFOLLOW_SELF(409, "不能取消关注自己"),

    // 业务逻辑错误 (4xx)
    ACCOUNT_DISABLED(400, "账号已被禁用"),
    ACCOUNT_NOT_ENABLED(400, "账号未启用"),
    EMAIL_NOT_VERIFIED(400, "邮箱未验证"),
    PHONE_NOT_VERIFIED(400, "手机号未验证"),
    PASSWORD_SAME_AS_OLD(400, "新密码不能与旧密码相同"),
    OLD_PASSWORD_ERROR(400, "原密码错误"),
    FOLLOW_COUNT_LIMIT(400, "关注数量达到上限"),
    FAN_COUNT_LIMIT(400, "粉丝数量达到上限"),
    BLACKLISTED_USER(400, "该用户已将你拉黑"),

    // 服务器错误 (5xx)
    SYSTEM_ERROR(500, "系统错误"),
    CREATE_USER_FAILED(500, "创建用户失败"),
    UPDATE_USER_FAILED(500, "更新用户失败"),
    DELETE_USER_FAILED(500, "删除用户失败"),
    GET_USER_INFO_FAILED(500, "获取用户信息失败"),
    GET_USER_LIST_FAILED(500, "获取用户列表失败"),
    LOGIN_FAILED(500, "登录失败"),
    REGISTER_FAILED(500, "注册失败"),
    FOLLOW_FAILED(500, "关注操作失败"),
    UNFOLLOW_FAILED(500, "取消关注失败"),
    SEND_VERIFICATION_CODE_FAILED(500, "发送验证码失败"),
    UPDATE_PASSWORD_FAILED(500, "修改密码失败"),
    DATABASE_ERROR(500, "数据库错误"),
    REDIS_ERROR(500, "缓存服务错误"),
    MQ_SEND_FAILED(500, "消息队列发送失败"),
    FILE_UPLOAD_FAILED(500, "文件上传失败"),
    THIRD_PARTY_API_ERROR(500, "第三方接口调用失败");

    private final int code;
    private final String msg;

    UserApiStatus(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public ApiResult<Void> response() {
        return response(null );
    }

    public <T> ApiResult<T> response(T data) {
        return new ApiResult<>(code, msg, data);
    }
}