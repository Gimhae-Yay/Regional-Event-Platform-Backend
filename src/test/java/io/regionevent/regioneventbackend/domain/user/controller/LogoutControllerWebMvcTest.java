package io.regionevent.regioneventbackend.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;

import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStoreUnavailableException;

@WebMvcTest({
    LoginController.class,
    LogoutController.class,
    MyRoleController.class,
    SignupController.class,
    WithdrawalController.class
})
class LogoutControllerWebMvcTest extends UserControllerWebMvcTestSupport {

    @BeforeEach
    void setUp() {
        reset(refreshTokenStore);
    }

    @Test
    void logout_유효한리프레시토큰_토큰패밀리를폐기하고쿠키를만료한다() throws Exception {
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
    void logout_리프레시토큰이없거나유효하지않음_저장소접근없이쿠키를만료한다() throws Exception {
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
    void logout_토큰저장소장애_쿠키를변경하지않고서비스이용불가를응답한다() throws Exception {
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
}
