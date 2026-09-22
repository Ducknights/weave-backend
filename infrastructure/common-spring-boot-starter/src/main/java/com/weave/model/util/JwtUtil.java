package com.weave.model.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weave.model.model.dto.UserDetailDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Log4j2
@RequiredArgsConstructor
public class JwtUtil {

    @Value("${weave.jwt.secret:TianYongChengWeaveBackendSecretKey2026}")
    private String secretString;

    private SecretKey secretKey;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JwtParser jwtParser;

    @PostConstruct
    public void init() {
        secretKey = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
        jwtParser = Jwts.parser().verifyWith(secretKey).build();
    }

    // 解析token获取用户信息，返回 UserDetailDto 对象
    public UserDetailDto getUserDetailFromJWT(String token) {
        try {
            Claims claims = jwtParser
                    .parseSignedClaims(token)
                    .getPayload();
            return objectMapper.convertValue(claims, UserDetailDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Token解析错误", e);
        }
    }

    // 解析token获取用户ID
    public Long getUserIdFromJWT (String token) {
        try { 
            Claims claims = jwtParser.parseSignedClaims(token).getPayload(); 
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) { 
            throw new RuntimeException ( "Token解析错误" , e);
        }
    }

    // 解析token获取token有效期
    public long getExpirationFromJWT(String token) {
        try {
            Claims claims = jwtParser
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration().getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            throw new RuntimeException("Token解析错误", e);
        }
    }

    // 生成token
    public String generateJwtToken(Long userId, Map<String, Object> claims, int expiration) {
        log.info("secretKey = {}, userId = {}", secretKey, userId);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(claims)
                .issuer("TianYongCheng")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(secretKey)
                .compact();
    }
}
