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

import io.regionevent.regioneventbackend.domain.payment.service.ResolvePaymentDiscrepancyResult;
import io.regionevent.regioneventbackend.domain.payment.service.ResolvePaymentDiscrepancyUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(ResolvePaymentDiscrepancyController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class ResolvePaymentDiscrepancyControllerWebMvcTest {

    private static final long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private ResolvePaymentDiscrepancyUseCase resolvePaymentDiscrepancyUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void resolve_유효한요청은문제없음종결결과를반환한다() throws Exception {
        when(resolvePaymentDiscrepancyUseCase.resolve(eq(USER_ID), eq(301L), any(), any())).thenReturn(
            new ResolvePaymentDiscrepancyResult(
                301L,
                "RESOLVED_NO_ISSUE",
                Instant.parse("2026-08-12T01:02:03Z")
            )
        );

        mockMvc.perform(authenticated(post(
                "/api/v1/platform-admin/payment-discrepancies/301/manual-actions"
            ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"evidenceReference":"PortOne 재조회 #4821","reason":"금액 일치를 확인"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("결제 불일치 문제없음 종결에 성공했습니다."))
            .andExpect(jsonPath("$.data.discrepancyId").value("301"))
            .andExpect(jsonPath("$.data.status").value("RESOLVED_NO_ISSUE"))
            .andExpect(jsonPath("$.data.resolvedAt").value("2026-08-12T01:02:03Z"));

        verify(resolvePaymentDiscrepancyUseCase).resolve(eq(USER_ID), eq(301L), any(), any());
    }

    @Test
    void resolve_양의십진Long이아닌식별자는종결하지않고유형오류를반환한다() throws Exception {
        mockMvc.perform(authenticated(post(
                "/api/v1/platform-admin/payment-discrepancies/01/manual-actions"
            ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"evidenceReference":"증빙","reason":"사유"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(resolvePaymentDiscrepancyUseCase);
    }

    @Test
    void resolve_공백입력과잘못된JSON은종결하지않고계약된오류를반환한다() throws Exception {
        mockMvc.perform(authenticated(post(
                "/api/v1/platform-admin/payment-discrepancies/301/manual-actions"
            ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"evidenceReference":"   ","reason":"사유"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(post(
                "/api/v1/platform-admin/payment-discrepancies/301/manual-actions"
            ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        verifyNoInteractions(resolvePaymentDiscrepancyUseCase);
    }

    @Test
    void resolve_종결된불일치는상태충돌을반환한다() throws Exception {
        when(resolvePaymentDiscrepancyUseCase.resolve(eq(USER_ID), eq(301L), any(), any()))
            .thenThrow(new BusinessException(ErrorCode.PAYMENT_DISCREPANCY_STATE_CONFLICT));

        mockMvc.perform(authenticated(post(
                "/api/v1/platform-admin/payment-discrepancies/301/manual-actions"
            ))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"evidenceReference":"증빙","reason":"사유"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PAYMENT_DISCREPANCY_STATE_CONFLICT"));
    }

    @Test
    void resolve_인증정보없음은종결하지않고미인증오류를반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/platform-admin/payment-discrepancies/301/manual-actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"evidenceReference":"증빙","reason":"사유"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(resolvePaymentDiscrepancyUseCase);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) {
        return request.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, USER_ID));
    }
}
