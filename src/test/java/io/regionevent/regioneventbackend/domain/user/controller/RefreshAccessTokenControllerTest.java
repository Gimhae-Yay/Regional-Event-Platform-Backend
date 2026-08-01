package io.regionevent.regioneventbackend.domain.user.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.regionevent.regioneventbackend.domain.user.dto.RefreshAccessTokenResult;
import io.regionevent.regioneventbackend.domain.user.service.RefreshAccessTokenUseCase;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.refresh.InvalidRefreshTokenException;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenConflictException;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStoreUnavailableException;

class RefreshAccessTokenControllerTest {

    private final RefreshAccessTokenUseCase refreshAccessTokenUseCase = mock(RefreshAccessTokenUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new RefreshAccessTokenController(refreshAccessTokenUseCase))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void refresh_whenTokenIsValid_returnsAccessHeaderAndRotatedCookie() throws Exception {
        when(refreshAccessTokenUseCase.reissue("current-token")).thenReturn(new RefreshAccessTokenResult(
            "access-token",
            "rotated-token",
            Duration.ofSeconds(60)
        ));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie("refreshToken", "current-token")))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=rotated-token")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=60")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("Access Token 재발급에 성공했습니다."))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void refresh_whenTokenIsMissing_returnsUnauthenticatedAndExpiresCookie() throws Exception {
        when(refreshAccessTokenUseCase.reissue(null)).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/v1/auth/refresh"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void refresh_whenRotationIsInProgress_returnsConflictWithoutChangingCookie() throws Exception {
        when(refreshAccessTokenUseCase.reissue("current-token"))
            .thenThrow(new RefreshTokenConflictException());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie("refreshToken", "current-token")))
            .andExpect(status().isConflict())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
            .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_CONFLICT"));
    }

    @Test
    void refresh_whenRedisIsUnavailable_returnsServiceUnavailableWithoutChangingCookie() throws Exception {
        when(refreshAccessTokenUseCase.reissue("current-token"))
            .thenThrow(new RefreshTokenStoreUnavailableException(new IllegalStateException()));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie("refreshToken", "current-token")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
            .andExpect(jsonPath("$.code").value("AUTH_SERVICE_UNAVAILABLE"));
    }
}
