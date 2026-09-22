package com.weave.model.model;

/**
 * API 状态码接口 —— 各服务 *ApiStatus 枚举的公共契约，
 * 供 exception-spring-boot-starter 中的 GlobalExceptionHandler 统一使用。
 */
public interface ApiStatus {

    int getCode();

    String getMsg();

    ApiResult<?> response();

    <T> ApiResult<T> response(T data);
}
