package com.weave.security.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weave.model.constant.Header;
import com.weave.model.util.SignatureUtil;
import com.weave.security.authentication.HeaderAuthenticationToken;
import com.weave.security.model.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class HeaderFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final SignatureUtil signatureUtil;
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 获取请求头中的用户信息
        String userIdStr = request.getHeader(Header.X_USER_ID);
        String rolesStr = request.getHeader(Header.X_USER_ROLES);
        String authoritiesStr = request.getHeader(Header.X_USER_AUTHORITIES);
        String signature = request.getHeader(Header.X_SIGNATURE);

        // 验签
        if (areParamsValid(userIdStr, rolesStr, authoritiesStr, signature) && signatureUtil.verifyHmac(userIdStr + rolesStr + authoritiesStr, signature)) {
            try {
                Long userId = Long.valueOf(userIdStr);
                List<String> roles = parseListHeader(rolesStr);
                List<String> authorities = parseListHeader(authoritiesStr);
                // 创建用户信息对象
                CustomUserDetails userDetails = new CustomUserDetails(userId, roles, authorities);
                // 创建认证令牌
                HeaderAuthenticationToken token = new HeaderAuthenticationToken(userDetails);
                // 将用户信息保存到认证上下文中
                SecurityContextHolder.getContext().setAuthentication(token);
                log.info("获取到用户信息 {}", userDetails);
            } catch (Exception e) {
                log.error("用户信息解析失败", e);
                SecurityContextHolder.clearContext();
            }
        } else {
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    // 解析列表头，反序列化为 List<String>
    private List<String> parseListHeader(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(headerValue, LIST_TYPE);
        } catch (Exception e) {
            log.warn("解析列表 header 失败: {}", headerValue, e);
            return Collections.emptyList();
        }
    }

    // 验证参数是否为空
    private boolean areParamsValid(String... params) {
        return Arrays.stream(params).allMatch(StringUtils::hasText);
    }
}
