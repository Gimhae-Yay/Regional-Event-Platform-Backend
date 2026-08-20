package io.regionevent.regioneventbackend.domain.user.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.regionevent.regioneventbackend.domain.user.dto.RefreshAccessTokenResult;
import io.regionevent.regioneventbackend.domain.user.service.RefreshAccessTokenUseCase;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.refresh.InvalidRefreshTokenException;

class RefreshAccessTokenControllerTest {

    private final RefreshAccessTokenUseCase refreshAccessTokenUseCase = mock(RefreshAccessTokenUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new RefreshAccessTokenController(refreshAccessTokenUseCase))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void refresh_유효한토큰_AccessToken만반환하고Cookie를교체하지않는다() throws Exception {
        when(refreshAccessTokenUseCase.reissue("current-token"))
            .thenReturn(new RefreshAccessTokenResult("access-token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie("refreshToken", "current-token")))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("Access Token 재발급에 성공했습니다."))
            .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    void refresh_토큰이없음_미인증오류와만료Cookie를반환한다() throws Exception {
        when(refreshAccessTokenUseCase.reissue(null)).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/v1/auth/refresh"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
