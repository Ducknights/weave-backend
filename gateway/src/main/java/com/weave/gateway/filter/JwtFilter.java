package com.weave.gateway.filter;

import com.weave.gateway.config.GatewayWhitelistProperties;
import com.weave.gateway.model.GatewayStatus;
import lombok.extern.slf4j.Slf4j;
import com.weave.util.JwtUtil;
import com.weave.model.constant.Header;
import com.weave.gateway.exception.BusinessException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class JwtFilter implements GlobalFilter {

    private final GatewayWhitelistProperties whitelistProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private List<GatewayWhitelistProperties.WhitelistEntry> whitelistEntries;

    public JwtFilter(GatewayWhitelistProperties whitelistProperties) {
        this.whitelistProperties = whitelistProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod httpMethod = request.getMethod();
        String jwt = request.getHeaders().getFirst(Header.AUTHORIZATION);

        // 1：尝试从请求头中提取并解析Token，获取UserID
        if (jwt != null && jwt.startsWith(Header.BEARER)){
            // 去掉Bearer前缀
            String token = jwt.substring(Header.BEARER.length());
            // 从JWT中提取UserID
            String userId = JwtUtil.getUserIdFromJWT(token);
            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(headers -> {
                        headers.remove(Header.AUTHORIZATION);
                        headers.set(Header.X_USER_ID, userId);
                    })
                    .build();
            exchange = exchange.mutate().request(mutatedRequest).build();
        }

        // 2: 检查请求是否在白名单中
        if (!isWhitelist(path, httpMethod) && jwt == null) {
            // 不在白名单，且无Token
            throw new BusinessException(GatewayStatus.NO_TOKEN);
        }

        // 3: 把请求向下传递
        return chain.filter(exchange);
    }

    private boolean isWhitelist(String path, HttpMethod httpMethod) {
        if (whitelistEntries == null) {
            whitelistEntries = whitelistProperties.parseEntries();
        }
        return whitelistEntries.stream()
                .anyMatch(entry -> {
                    boolean methodMatch = entry.method() == null || entry.method() == httpMethod;
                    boolean pathMatch = pathMatcher.match(entry.pathPattern(), path);
                    return methodMatch && pathMatch;
                });
    }
}
