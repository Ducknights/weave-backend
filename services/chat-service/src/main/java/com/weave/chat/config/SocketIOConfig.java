package com.weave.chat.config;

import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.SpringAnnotationScanner;
import com.corundumstudio.socketio.protocol.JacksonJsonSupport;
import com.corundumstudio.socketio.protocol.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.weave.redis.util.RedisUtil;
import com.weave.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import com.weave.redis.constant.CacheKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.lang.reflect.Field;

@Log4j2
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SocketIOConfig {

    @Value("${socketio.host:0.0.0.0}")
    private String host;
    @Value("${socketio.port:4301}")
    private int port;

    private final RedisUtil redisUtil;

    @Bean
    public SocketIOServer socketIOServer() {
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
        config.setHostname(host);
        config.setPort(port);

        SocketConfig socketConfig = new SocketConfig();
        socketConfig.setReuseAddress(true);
        config.setSocketConfig(socketConfig);

        config.setOrigin("*");
        config.setPingInterval(25000);
        config.setPingTimeout(60000);

        config.setAuthorizationListener(data -> {
            String token = data.getSingleUrlParam("token");
            if (token == null || token.isEmpty()) {
                return AuthorizationResult.FAILED_AUTHORIZATION;
            }
            try {
                String subject = JwtUtil.getJwtSubject(token);
                String userIdStr = subject.substring(subject.lastIndexOf(":") + 1);
                Long userId = Long.valueOf(userIdStr);
                String key = CacheKey.buildCacheKey(CacheKey.USER_AUTHORITY, userId);
                if (Boolean.FALSE.equals(redisUtil.hasKey(key))) {
                    return AuthorizationResult.FAILED_AUTHORIZATION;
                }
                return AuthorizationResult.SUCCESSFUL_AUTHORIZATION;
            } catch (Exception e) {
                return AuthorizationResult.FAILED_AUTHORIZATION;
            }
        });

        JsonSupport jsonSupport = new JacksonJsonSupport(new JavaTimeModule());
        try {
            Field field = JacksonJsonSupport.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            ObjectMapper mapper = (ObjectMapper) field.get(jsonSupport);
            // 禁用时间戳，输出为字符串（ISO-8601）
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            // 默认 ISO 格式即可，前端 new Date() 可直接解析
        } catch (Exception e) {
            log.error("修改 JacksonJsonSupport 失败", e);
        }
        config.setJsonSupport(jsonSupport);
        return new SocketIOServer(config);
    }

    @Bean
    public SpringAnnotationScanner springAnnotationScanner(SocketIOServer socketIOServer) {
        return new SpringAnnotationScanner(socketIOServer);
    }
}
