package io.regionevent.regioneventbackend.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import io.regionevent.regioneventbackend.domain.user.dto.LoginResponse;
import io.regionevent.regioneventbackend.domain.user.dto.LoginResult;
import io.regionevent.regioneventbackend.domain.user.service.LoginUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStoreUnavailableException;

@WebMvcTest({
    LoginController.class,
    LogoutController.class,
    MyRoleController.class,
    SignupController.class,
    WithdrawalController.class
})
class LoginControllerWebMvcTest extends UserControllerWebMvcTestSupport {

    @Test
    void login_유효한요청_토큰과역할을응답한다() throws Exception {
        when(loginUseCase.login(any())).thenReturn(new LoginResult(
            new LoginResponse("100", List.of("VISITOR"), "access-token"),
            "refresh-token"
        ));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": " Visitor@Example.com ",
                      "password": "LocalStamp!2026"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("refreshToken=refresh-token")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=1209600")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Path=/api/v1/auth")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("HttpOnly")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Secure")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("SameSite=Strict")))
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("로그인에 성공했습니다."))
            .andExpect(jsonPath("$.data.userId").value("100"))
            .andExpect(jsonPath("$.data.roles[0]").value("VISITOR"))
            .andExpect(jsonPath("$.data.accessToken").value("access-token"))
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

        verify(loginUseCase).login(any());
    }

    @Test
    void login_입력이유효하지않음_입력오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "not-an-email",
                      "password": "   "
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verifyNoInteractions(loginUseCase);
    }

    @Test
    void login_자격증명이유효하지않음_토큰없이인증오류를응답한다() throws Exception {
        when(loginUseCase.login(any())).thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void login_토큰저장소장애_서비스이용불가를응답한다() throws Exception {
        when(loginUseCase.login(any())).thenThrow(
            new RefreshTokenStoreUnavailableException(new IllegalStateException("Redis unavailable"))
        );

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("AUTH_SERVICE_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("인증 서비스를 일시적으로 사용할 수 없습니다."))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private String loginRequest() {
        return """
            {
              "email": "visitor@example.com",
              "password": "LocalStamp!2026"
            }
            """;
    }
}
