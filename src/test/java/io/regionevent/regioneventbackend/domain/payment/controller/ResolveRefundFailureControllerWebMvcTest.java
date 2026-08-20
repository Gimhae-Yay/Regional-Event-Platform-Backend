package io.regionevent.regioneventbackend.domain.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import io.regionevent.regioneventbackend.domain.payment.service.ResolveRefundFailureResult;
import io.regionevent.regioneventbackend.domain.payment.service.ResolveRefundFailureUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(ResolveRefundFailureController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class ResolveRefundFailureControllerWebMvcTest {

    private static final long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private ResolveRefundFailureUseCase resolveRefundFailureUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void resolve_유효한요청은수동조치결과를반환한다() throws Exception {
        when(resolveRefundFailureUseCase.resolve(eq(USER_ID), eq(552L), any(), any())).thenReturn(
            new ResolveRefundFailureResult(552L, "FAILED", Instant.parse("2026-08-12T01:02:03Z"))
        );

        mockMvc.perform(authenticated(post("/api/v1/platform-admin/refund-failures/552/manual-actions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"confirmedStatus":"FAILED","evidenceReference":"PortOne 재조회 #5013","reason":"실제 미처리 확인"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("환불 실패 수동 조치에 성공했습니다."))
            .andExpect(jsonPath("$.data.refundId").value("552"))
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.resolvedAt").value("2026-08-12T01:02:03Z"));

        verify(resolveRefundFailureUseCase).resolve(eq(USER_ID), eq(552L), any(), any());
    }

    @Test
    void resolve_식별자나요청값이계약에맞지않으면호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/platform-admin/refund-failures/01/manual-actions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"confirmedStatus":"FAILED","evidenceReference":"증빙","reason":"사유"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(authenticated(post("/api/v1/platform-admin/refund-failures/552/manual-actions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"confirmedStatus":"DISCREPANT","evidenceReference":" ","reason":"사유"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(resolveRefundFailureUseCase);
    }

    @Test
    void resolve_종결된환불은상태충돌을반환한다() throws Exception {
        when(resolveRefundFailureUseCase.resolve(eq(USER_ID), eq(552L), any(), any()))
            .thenThrow(new BusinessException(ErrorCode.REFUND_STATE_CONFLICT));

        mockMvc.perform(authenticated(post("/api/v1/platform-admin/refund-failures/552/manual-actions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"confirmedStatus":"SUCCEEDED","evidenceReference":"증빙","reason":"사유"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("REFUND_STATE_CONFLICT"));
    }

    @Test
    void resolve_미인증이면수동조치를호출하지않는다() throws Exception {
        mockMvc.perform(post("/api/v1/platform-admin/refund-failures/552/manual-actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"confirmedStatus":"FAILED","evidenceReference":"증빙","reason":"사유"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(resolveRefundFailureUseCase);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) {
        return request.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, USER_ID));
    }
}
