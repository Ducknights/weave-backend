package com.weave.auth.controller;

import com.weave.auth.model.dto.*;
import com.weave.util.JwtUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.weave.model.model.ApiResult;
import com.weave.auth.model.enums.AuthApiStatus;
import com.weave.auth.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.weave.model.constant.Header;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    private static final String REFRESH_PATH = "/refresh";
    private static final Duration REFRESH_TOKEN_EXPIRE = Duration.ofDays(7);

    /**
     * 登录
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResult<LoginResDto>> login(@Valid @NotNull @RequestBody ApiRequestDto apiRequestDto) {
        // 验证登录，并返回用户信息
        UserDto userDto = authService.login(apiRequestDto);
        // 获取访问令牌
        TokenDto tokenDto = authService.getAccessToken(userDto.getId());
        // 获取刷新令牌（httponly，7天有效期）
        String refreshToken = authService.getRefreshToken(userDto.getId());
        ResponseCookie cookie = ResponseCookie.from(Header.REFRESH_TOKEN, refreshToken)
                .httpOnly(true)
                .path(REFRESH_PATH)
                .maxAge(REFRESH_TOKEN_EXPIRE)
                .build();
        // 组装登录结果
        LoginResDto loginResDto = new LoginResDto(tokenDto, userDto);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(AuthApiStatus.LOGIN_SUCCESS.response(loginResDto));
    }

    /**
     * 发送验证码
     */
    @PostMapping("/register/code")
    public ResponseEntity<ApiResult<Void>> sendCode(@Valid @NotNull @RequestBody ApiRequestDto apiRequestDto) {
        authService.sendCode(apiRequestDto);
        return ResponseEntity.ok()
                .body(AuthApiStatus.CODE_SEND_SUCCESS.response());
    }

    /**
     * 验证验证码
     */
    @PostMapping("/register/code/verify")
    public ResponseEntity<ApiResult<Void>> verify(@Valid @NotNull @RequestBody VerifyCodeDto dto) {
        log.info("verify: {}", dto);
        authService.verifyCode(dto);
        return ResponseEntity.status(201)
                .body(AuthApiStatus.REGISTER_SUCCESS.response());
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResult<Void>> logout(@CookieValue(value = Header.REFRESH_TOKEN) String refreshToken) {
        Long userId = Long.valueOf(JwtUtil.getUserIdFromJWT(refreshToken));
        // 清除当前用户的登录信息
        authService.logout(userId);
        // 清除刷新令牌
        ResponseCookie cookie = ResponseCookie.from(Header.REFRESH_TOKEN, "")
                .httpOnly(true)
                .path(REFRESH_PATH)
                .maxAge(0L)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(AuthApiStatus.LOGOUT_SUCCESS.response());
    }

    /**
     * 刷新令牌
     */
    @GetMapping("/refresh")
    public ResponseEntity<ApiResult<TokenDto>> getNewRefreshToken(@CookieValue(value = Header.REFRESH_TOKEN) String refreshToken) {
        // 刷新访问令牌
        TokenDto dto = authService.getNewAccessToken(refreshToken);
        // 刷新refresh token
        Optional<String> newRefreshToken = authService.getNewRefreshToken(refreshToken);
        // 根据有无新刷新令牌，决定是否添加cookie
        return newRefreshToken.map(token -> {
            ResponseCookie cookie = ResponseCookie.from(Header.REFRESH_TOKEN, token)
                    .httpOnly(true)
                    .path(REFRESH_PATH)
                    .maxAge(REFRESH_TOKEN_EXPIRE)
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(AuthApiStatus.NEW_TOKEN_SUCCESS.response(dto));
        }).orElseGet(() -> ResponseEntity.ok()
                .body(AuthApiStatus.NEW_TOKEN_SUCCESS.response(dto)));
    }

}
