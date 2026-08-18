package io.regionevent.regioneventbackend.domain.user.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.user.dto.LoginRequest;
import io.regionevent.regioneventbackend.domain.user.dto.LoginResult;
import io.regionevent.regioneventbackend.domain.user.dto.LoginResponse;
import io.regionevent.regioneventbackend.domain.user.service.LoginUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;
import io.regionevent.regioneventbackend.global.security.refresh.JwtRefreshTokenService;

@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

    private static final String LOGIN_SUCCESS_MESSAGE = "로그인에 성공했습니다.";
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth";

    private final LoginUseCase loginUseCase;

    public LoginController(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginUseCase.login(request);
        ResponseCookie refreshTokenCookie = createRefreshTokenCookie(result.refreshToken());
        ApiResponse<LoginResponse> response = ApiResponse.success(HttpStatus.OK, LOGIN_SUCCESS_MESSAGE, result.response());

        return ResponseEntity
            .status(HttpStatus.OK)
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
            .body(response);
    }

    private ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
            .maxAge(JwtRefreshTokenService.REFRESH_TOKEN_TTL)
            .path(REFRESH_TOKEN_COOKIE_PATH)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .build();
    }
}
