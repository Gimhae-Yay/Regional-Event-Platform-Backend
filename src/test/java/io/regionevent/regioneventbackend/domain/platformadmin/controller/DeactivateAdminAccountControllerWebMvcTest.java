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

import io.regionevent.regioneventbackend.domain.platformadmin.service.DeactivateAdminAccountResult;
import io.regionevent.regioneventbackend.domain.platformadmin.service.DeactivateAdminAccountUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(DeactivateAdminAccountController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class DeactivateAdminAccountControllerWebMvcTest {

    private static final long SUPER_ADMIN_USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private DeactivateAdminAccountUseCase deactivateAdminAccountUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void deactivateAdminAccount_유효한요청_성공응답을반환한다() throws Exception {
        when(deactivateAdminAccountUseCase.deactivate(eq(SUPER_ADMIN_USER_ID), eq(101L), any(), any()))
            .thenReturn(new DeactivateAdminAccountResult(
                101L,
                201L,
                "PLATFORM_ADMIN",
                "INACTIVE",
                Instant.parse("2026-08-10T01:00:00Z")
            ));

        mockMvc.perform(authenticated(post("/api/v1/platform-admin/admin-accounts/101/deactivate"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.data.userId").value("101"))
            .andExpect(jsonPath("$.data.platformAdminAssignmentId").value("201"))
            .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        verify(deactivateAdminAccountUseCase).deactivate(eq(SUPER_ADMIN_USER_ID), eq(101L), any(), any());
    }

    @Test
    void deactivateAdminAccount_자기자신비활성화충돌을응답한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.ADMIN_ACCOUNT_DEACTIVATION_CONFLICT))
            .when(deactivateAdminAccountUseCase).deactivate(eq(SUPER_ADMIN_USER_ID), eq(101L), any(), any());

        mockMvc.perform(authenticated(post("/api/v1/platform-admin/admin-accounts/101/deactivate"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ADMIN_ACCOUNT_DEACTIVATION_CONFLICT"));
    }

    @Test
    void deactivateAdminAccount_슈퍼관리자가아님_권한오류를응답한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
            .when(deactivateAdminAccountUseCase).deactivate(eq(SUPER_ADMIN_USER_ID), eq(101L), any(), any());

        mockMvc.perform(authenticated(post("/api/v1/platform-admin/admin-accounts/101/deactivate"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void deactivateAdminAccount_잘못된대상식별자_입력오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/platform-admin/admin-accounts/0/deactivate"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(deactivateAdminAccountUseCase);
    }

    private String validRequest() {
        return """
            {
              "reasonCode": "ADMIN_ACCOUNT_INACTIVATION",
              "evidenceReference": "OPS-2026-0810-001"
            }
            """;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(SUPER_ADMIN_USER_ID));
    }
}
