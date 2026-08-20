package io.regionevent.regioneventbackend.domain.user.controller;

import jakarta.servlet.http.Cookie;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.user.dto.RefreshAccessTokenResult;
import io.regionevent.regioneventbackend.domain.user.dto.RefreshAccessTokenResponse;
import io.regionevent.regioneventbackend.domain.user.service.RefreshAccessTokenUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenCookie;

@RestController
@RequestMapping("/api/v1/auth")
public class RefreshAccessTokenController {

    private static final String REFRESH_SUCCESS_MESSAGE = "Access Token 재발급에 성공했습니다.";

    private final RefreshAccessTokenUseCase refreshAccessTokenUseCase;

    public RefreshAccessTokenController(RefreshAccessTokenUseCase refreshAccessTokenUseCase) {
        this.refreshAccessTokenUseCase = refreshAccessTokenUseCase;
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshAccessTokenResponse>> refresh(
        @CookieValue(value = "refreshToken", required = false) Cookie refreshToken
    ) {
        RefreshAccessTokenResult result = refreshAccessTokenUseCase.reissue(
            refreshToken == null ? null : refreshToken.getValue()
        );
        return ResponseEntity
            .status(HttpStatus.OK)
            .header(
                HttpHeaders.SET_COOKIE,
                RefreshTokenCookie.create(result.refreshToken(), result.refreshTokenMaxAge())
            )
            .body(ApiResponse.success(
                HttpStatus.OK,
                REFRESH_SUCCESS_MESSAGE,
                new RefreshAccessTokenResponse(result.accessToken())
            ));
    }
}
