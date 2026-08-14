package com.weave.gateway.filter;

import com.weave.gateway.exception.BusinessException;
import com.weave.gateway.model.GatewayStatus;
import com.weave.redis.util.BloomUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Order(-2)
@Component
@RequiredArgsConstructor
public class BloomFilter implements GlobalFilter {

    private final BloomUtil bloomUtil;

    /**
     * 网关层只拦截可能产生缓存穿透的"按ID查单条资源"类请求。
     * 列表页、健康检查、非ID路径直接放行。
     */
    private static final List<BloomRouteRule> ROUTE_RULES = List.of(
            new BloomRouteRule(
                    Pattern.compile("^/api/posts?/(\\d+)(?:/.*)?$"),
                    FilterType.POST,
                    List.of("hot", "new", "recommend", "hidden", "health", "user", "batch")
            ),
            new BloomRouteRule(
                    Pattern.compile("^/api/users?/(\\d+)/(?:info|detail)$"),
                    FilterType.USER,
                    List.of()
            ),
            new BloomRouteRule(
                    Pattern.compile("^/api/comment(?:/replies)?/(\\w+)(?:/.*)?$"),
                    FilterType.COMMENT,
                    List.of("post")
            )
    );

    private static final List<String> STATIC_BYPASS_SEGMENTS = List.of(
            "auth", "chat", "club", "draft", "rag", "recommend", "search", "socket.io"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        PathContainer container = request.getPath();

        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }
        String apiPrefix = container.elements().size() >= 2
                ? container.elements().get(1).value()
                : "";
        if (STATIC_BYPASS_SEGMENTS.contains(apiPrefix)) {
            return chain.filter(exchange);
        }
        MatchResult match = matchRoute(path);
        if (match == null) {
            return chain.filter(exchange);
        }

        return Mono.fromCallable(() -> switch (match.type) {
                    case POST    -> bloomUtil.containsPost(match.id);
                    case USER    -> bloomUtil.containsUser(match.id);
                    case COMMENT -> bloomUtil.containsComment(match.id);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(exists -> {
                    if (Boolean.FALSE.equals(exists)) {
                        log.debug("[Bloom] 拦截不存在的资源: type={}, id={}, path={}",
                                match.type, match.id, path);
                        return Mono.error(new BusinessException(GatewayStatus.RESOURCE_NOT_FOUND));
                    }
                    return chain.filter(exchange);
                });
    }

    private MatchResult matchRoute(String path) {
        for (BloomRouteRule rule : ROUTE_RULES) {
            String afterService = stripApiPrefix(path, rule.type);
            if (afterService != null && !rule.excludeFirstSegments().isEmpty()) {
                String firstSeg = firstSegment(afterService);
                if (firstSeg != null && rule.excludeFirstSegments().contains(firstSeg)) {
                    continue;
                }
            }
            Matcher m = rule.pattern().matcher(path);
            if (!m.matches()) {
                continue;
            }
            String rawId = m.group(1);
            Object id = switch (rule.type) {
                case POST, USER -> {
                    try { yield Long.parseLong(rawId); }
                    catch (NumberFormatException ignored) { yield null; }
                }
                case COMMENT -> rawId;
            };
            if (id == null) {
                continue;
            }
            return new MatchResult(rule.type(), id);
        }
        return null;
    }

    private String stripApiPrefix(String path, FilterType type) {
        return switch (type) {
            case POST -> stripPrefix(path, "/api/post/");
            case USER -> stripPrefix(path, "/api/user/");
            case COMMENT -> stripPrefix(path, "/api/comment/");
        };
    }

    private static String stripPrefix(String path, String prefix) {
        return path.startsWith(prefix) ? path.substring(prefix.length()) : null;
    }

    private static String firstSegment(String rest) {
        int slash = rest.indexOf('/');
        return slash == -1 ? rest : rest.substring(0, slash);
    }

    private enum FilterType {POST, USER, COMMENT}

    private record BloomRouteRule(Pattern pattern,
                                  FilterType type,
                                  List<String> excludeFirstSegments) {}

    private record MatchResult(FilterType type, Object id) {}
}
