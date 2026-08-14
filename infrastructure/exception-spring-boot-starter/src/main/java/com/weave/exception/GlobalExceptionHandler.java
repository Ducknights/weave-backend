package com.weave.exception;

import com.weave.model.model.ApiResult;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器 —— 抽取自各服务的重复异常处理逻辑，放入 starter 统一管理。
 * 仅在 Servlet (MVC) 环境下生效，WebFlux (Gateway) 服务自行维护异常处理器。
 * 各服务可提供自己的 handler 以更高优先级覆盖。
 */
@Log4j2
@RestControllerAdvice
@Order()
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    /**
     * 处理业务异常 —— 各服务的 BusinessException 均继承 AbstractBusinessException
     */
    @ExceptionHandler(AbstractBusinessException.class)
    public ResponseEntity<ApiResult<?>> handleBusinessException(AbstractBusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ResponseEntity.status(e.getStatus().getCode())
                .body(e.getStatus().response());
    }

    /**
     * 处理参数校验异常 (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Map<String, Object>>> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("参数校验失败: {}", e.getMessage());

        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> data = new HashMap<>();
        data.put("errors", errors);

        return ResponseEntity.badRequest()
                .body(new ApiResult<>(400, "参数校验失败", data));
    }

    /**
     * 处理请求体格式错误
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<?>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ApiResult<>(400, "请求体格式错误", null));
    }

    /**
     * 处理缺少请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResult<?>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return ResponseEntity.badRequest()
                .body(new ApiResult<>(400, "缺少必要参数: " + e.getParameterName(), null));
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResult<?>> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常:", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResult<>(500, "系统错误", null));
    }

    /**
     * 处理所有未捕获的异常（兜底）
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<?>> handleException(Exception e) {
        log.error("系统异常:", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResult<>(500, "系统错误", null));
    }
}
