package io.regionevent.regioneventbackend.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStoreUnavailableException;

@SpringBootTest
@AutoConfigureMockMvc
@Import(LogoutControllerIntegrationTest.LogoutTestConfiguration.class)
class LogoutControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @BeforeEach
    void setUp() {
        reset(refreshTokenStore);
    }

    @Test
    void logout_withActiveRefreshToken_revokesFamilyAndExpiresCookie() throws Exception {
        String refreshToken = refreshTokenService.issue(1L);
        reset(refreshTokenStore);

        mockMvc.perform(post("/api/v1/auth/logout")
                .cookie(new Cookie("refreshToken", refreshToken)))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("refreshToken=")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Path=/api/v1/auth")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("HttpOnly")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Secure")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("SameSite=Strict")))
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("로그아웃에 성공했습니다."))
            .andExpect(jsonPath("$.data").isEmpty());

        verify(refreshTokenStore).revokeFamily(any());
    }

    @Test
    void logout_withoutOrWithInvalidRefreshToken_expiresCookieWithoutStoreAccess() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                .cookie(new Cookie("refreshToken", "invalid")))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")))
            .andExpect(jsonPath("$.code").value("SUCCESS"));

        mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")))
            .andExpect(jsonPath("$.code").value("SUCCESS"));

        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    void logout_whenRefreshTokenStoreIsUnavailable_returnsServiceUnavailableWithoutCookieChange() throws Exception {
        String refreshToken = refreshTokenService.issue(1L);
        reset(refreshTokenStore);
        doThrow(new RefreshTokenStoreUnavailableException(new IllegalStateException("Redis unavailable")))
            .when(refreshTokenStore)
            .revokeFamily(any());

        mockMvc.perform(post("/api/v1/auth/logout")
                .cookie(new Cookie("refreshToken", refreshToken)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.statusCode").value(503))
            .andExpect(jsonPath("$.code").value("AUTH_SERVICE_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("인증 서비스를 일시적으로 사용할 수 없습니다."))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @TestConfiguration
    static class LogoutTestConfiguration {

        @Bean
        @Primary
        RefreshTokenStore refreshTokenStore() {
            return mock(RefreshTokenStore.class);
        }
    }
}
