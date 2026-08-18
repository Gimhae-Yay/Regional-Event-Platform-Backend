package io.regionevent.regioneventbackend.domain.payment.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.payment.service.GetPaymentDiscrepanciesUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.PaymentDiscrepancyListInfo;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(PaymentDiscrepancyController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class PaymentDiscrepancyControllerWebMvcTest {

    private static final long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetPaymentDiscrepanciesUseCase getPaymentDiscrepanciesUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void getDiscrepancies_기본상태로민감정보없이목록을반환한다() throws Exception {
        when(getPaymentDiscrepanciesUseCase.get(USER_ID, "OPEN")).thenReturn(List.of(discrepancy()));

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/payment-discrepancies")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("결제 불일치 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.discrepancies[0].discrepancyId").value("301"))
            .andExpect(jsonPath("$.data.discrepancies[0].paymentId").value("902"))
            .andExpect(jsonPath("$.data.discrepancies[0].discrepancyType").value("AMOUNT_MISMATCH"))
            .andExpect(jsonPath("$.data.discrepancies[0].status").value("OPEN"))
            .andExpect(jsonPath("$.data.discrepancies[0].finalAmount").value(15_000))
            .andExpect(jsonPath("$.data.discrepancies[0].currency").value("KRW"))
            .andExpect(jsonPath("$.data.discrepancies[0].detectedAt").value("2026-08-06T03:05:00Z"))
            .andExpect(jsonPath("$.data.discrepancies[0].portonePaymentId").doesNotExist())
            .andExpect(jsonPath("$.data.discrepancies[0].orderId").doesNotExist());

        verify(getPaymentDiscrepanciesUseCase).get(USER_ID, "OPEN");
    }

    @Test
    void getDiscrepancies_명시한허용상태는그대로조회한다() throws Exception {
        when(getPaymentDiscrepanciesUseCase.get(USER_ID, "REFUND_REQUESTED")).thenReturn(List.of());

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/payment-discrepancies")
                .queryParam("status", "REFUND_REQUESTED")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.discrepancies").isArray())
            .andExpect(jsonPath("$.data.discrepancies").isEmpty());

        verify(getPaymentDiscrepanciesUseCase).get(USER_ID, "REFUND_REQUESTED");
    }

    @Test
    void getDiscrepancies_허용하지않은상태는조회하지않고입력오류를반환한다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/platform-admin/payment-discrepancies")
                .queryParam("status", "CLOSED")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(getPaymentDiscrepanciesUseCase);
    }

    @Test
    void getDiscrepancies_권한없음은계약된오류를반환한다() throws Exception {
        when(getPaymentDiscrepanciesUseCase.get(USER_ID, "OPEN"))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/payment-discrepancies")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getDiscrepancies_인증정보없음은조회하지않고미인증오류를반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/platform-admin/payment-discrepancies"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getPaymentDiscrepanciesUseCase);
    }

    private PaymentDiscrepancyListInfo discrepancy() {
        return new PaymentDiscrepancyListInfo(
            301L,
            902L,
            "AMOUNT_MISMATCH",
            "OPEN",
            15_000L,
            "KRW",
            Instant.parse("2026-08-06T03:05:00Z")
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) {
        return request.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, USER_ID));
    }
}
