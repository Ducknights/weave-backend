package com.weave.gateway.sentinel;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weave.gateway.model.GatewayStatus;
import com.weave.model.model.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Sentinel 限流/熔断触发时的统一响应。
 * 返回格式与 GatewayExceptionHandler（ApiResult）保持一致，HTTP 429 Too Many Requests。
 */
@Slf4j
public class SentinelGatewayBlockHandler implements BlockRequestHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable t) {
        log.warn("[Sentinel] 触发流控规则: uri={}, rule={}",
                exchange.getRequest().getPath(),
                t.getMessage());
        ApiResult<?> body = GatewayStatus.TOO_MANY_REQUESTS.response();
        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toJson(body));
    }

    private String toJson(ApiResult<?> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return "{\"code\":429,\"msg\":\"请求过于频繁，请稍后再试\",\"data\":null}";
        }
    }
}
