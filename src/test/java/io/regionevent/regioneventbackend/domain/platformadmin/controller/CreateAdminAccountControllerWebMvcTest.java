package io.regionevent.regioneventbackend.domain.platformadmin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.platformadmin.service.CreateAdminAccountResult;
import io.regionevent.regioneventbackend.domain.platformadmin.service.CreateAdminAccountUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(CreateAdminAccountController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class CreateAdminAccountControllerWebMvcTest {

    private static final long SUPER_ADMIN_USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private CreateAdminAccountUseCase createAdminAccountUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void createAdminAccount_유효한요청_생성응답을반환한다() throws Exception {
        when(createAdminAccountUseCase.create(eq(SUPER_ADMIN_USER_ID), any(), any())).thenReturn(
            new CreateAdminAccountResult(
                101L,
                201L,
                "PLATFORM_ADMIN",
                "ACTIVE",
                Instant.parse("2026-08-09T06:00:00Z")
            )
        );

        mockMvc.perform(authenticated(post("/api/v1/platform-admin/admin-accounts"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.userId").value("101"))
            .andExpect(jsonPath("$.data.platformAdminAssignmentId").value("201"))
            .andExpect(jsonPath("$.data.grade").value("PLATFORM_ADMIN"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.createdAt").value("2026-08-09T06:00:00Z"));

        verify(createAdminAccountUseCase).create(eq(SUPER_ADMIN_USER_ID), any(), any());
    }

    @Test
    void createAdminAccount_잘못된등급_입력오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/platform-admin/admin-accounts"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest().replace("PLATFORM_ADMIN", "VISITOR")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(createAdminAccountUseCase);
    }

    @Test
    void createAdminAccount_중복이메일_중복오류를응답한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.DUPLICATE_LOGIN_IDENTIFIER))
            .when(createAdminAccountUseCase).create(eq(SUPER_ADMIN_USER_ID), any(), any());

        mockMvc.perform(authenticated(post("/api/v1/platform-admin/admin-accounts"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_IDENTIFIER"));
    }

    @Test
    void createAdminAccount_슈퍼관리자가아님_권한오류를응답한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
            .when(createAdminAccountUseCase).create(eq(SUPER_ADMIN_USER_ID), any(), any());

        mockMvc.perform(authenticated(post("/api/v1/platform-admin/admin-accounts"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void createAdminAccount_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/platform-admin/admin-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private String validRequest() {
        return """
            {
              "email": "admin@example.com",
              "password": "LocalStamp!2026",
              "name": "관리자",
              "phone": "010-1234-5678",
              "grade": "PLATFORM_ADMIN",
              "reasonCode": "account-creation/v1",
              "evidenceReference": "OPS-2026-0806-001"
            }
            """;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(SUPER_ADMIN_USER_ID));
    }
}
