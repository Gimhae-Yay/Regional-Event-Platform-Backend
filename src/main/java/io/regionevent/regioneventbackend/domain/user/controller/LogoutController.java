package io.regionevent.regioneventbackend.domain.user.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.global.response.ApiResponse;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenCookie;

@RestController
@RequestMapping("/api/v1/auth")
public class LogoutController {

    private static final String LOGOUT_SUCCESS_MESSAGE = "로그아웃에 성공했습니다.";
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity
            .status(HttpStatus.OK)
            .header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.expire())
            .body(ApiResponse.success(HttpStatus.OK, LOGOUT_SUCCESS_MESSAGE));
    }
}
