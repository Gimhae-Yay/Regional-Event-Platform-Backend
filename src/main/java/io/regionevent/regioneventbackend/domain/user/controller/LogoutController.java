package io.regionevent.regioneventbackend.domain.user.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.global.response.ApiResponse;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;

@RestController
@RequestMapping("/api/v1/auth")
public class LogoutController {

    private static final String LOGOUT_SUCCESS_MESSAGE = "로그아웃에 성공했습니다.";
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth";

    private final RefreshTokenService refreshTokenService;

    public LogoutController(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
        @CookieValue(value = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken
    ) {
        if (refreshToken != null) {
            refreshTokenService.revokeCurrentFamily(refreshToken);
        }
        return ResponseEntity
            .status(HttpStatus.OK)
            .header(HttpHeaders.SET_COOKIE, expiredRefreshTokenCookie().toString())
            .body(ApiResponse.success(HttpStatus.OK, LOGOUT_SUCCESS_MESSAGE));
    }

    private ResponseCookie expiredRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
            .maxAge(0)
            .path(REFRESH_TOKEN_COOKIE_PATH)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .build();
    }
}
