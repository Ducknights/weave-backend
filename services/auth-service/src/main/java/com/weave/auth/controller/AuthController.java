package com.weave.auth.controller;

import com.weave.auth.model.dto.LoginResDto;
import com.weave.auth.model.dto.TokenDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.weave.auth.model.dto.ApiRequestDto;
import com.weave.model.model.ApiResult;
import com.weave.auth.model.dto.VerifyCodeDto;
import com.weave.auth.model.enums.AuthApiStatus;
import com.weave.auth.service.AuthService;
import com.weave.security.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResult<LoginResDto>> login(@Valid @NotNull @RequestBody ApiRequestDto apiRequestDto) {
        log.info("login: {}", apiRequestDto);
        LoginResDto apiResult = authService.login(apiRequestDto);
        return ResponseEntity.ok()
                .body(AuthApiStatus.LOGIN_SUCCESS.response(apiResult));
    }

    @PostMapping("/register/code")
    public ResponseEntity<ApiResult<Void>> sendCode(@Valid @NotNull @RequestBody ApiRequestDto apiRequestDto) {
        authService.sendCode(apiRequestDto);
        return ResponseEntity.ok()
                .body(AuthApiStatus.CODE_SEND_SUCCESS.response());
    }

    @PostMapping("/register/code/verify")
    public ResponseEntity<ApiResult<Void>> verify(@Valid @NotNull @RequestBody VerifyCodeDto dto) {
        log.info("verify: {}", dto);
        authService.verifyCode(dto);
        return ResponseEntity.status(201)
                .body(AuthApiStatus.REGISTER_SUCCESS.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResult<Void>> logout() {
        Long userId = SecurityUtils.getCurrentUserId();
        authService.logout(userId);
        return ResponseEntity.ok()
                .body(AuthApiStatus.LOGOUT_SUCCESS.response());
    }

    @GetMapping("/access")
    public ResponseEntity<ApiResult<TokenDto>> getNewToken(@RequestHeader(com.weave.model.constant.RequestHeader.X_USER_ID) String userId) {
        log.info("getNewToken: {}", userId);
        TokenDto dto = authService.getNewSuccessToken(Long.valueOf(userId));
        return ResponseEntity.ok()
                .body(AuthApiStatus.NEW_TOKEN_SUCCESS.response(dto));
    }

    @GetMapping("/refresh")
    public ResponseEntity<ApiResult<TokenDto>> getNewRefreshToken(@RequestHeader(com.weave.model.constant.RequestHeader.X_USER_ID) String userId) {
        TokenDto dto = authService.getNewRefreshToken(Long.valueOf(userId));
        return ResponseEntity.ok()
                .body(AuthApiStatus.NEW_TOKEN_SUCCESS.response(dto));
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok().body("服务运行正常");
    }
}
