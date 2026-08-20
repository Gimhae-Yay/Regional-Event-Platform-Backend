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

import io.regionevent.regioneventbackend.domain.payment.service.GetPaymentDiscrepancyUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.PaymentDiscrepancyDetailInfo;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(PaymentDiscrepancyDetailController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class PaymentDiscrepancyDetailControllerWebMvcTest {

    private static final long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetPaymentDiscrepancyUseCase getPaymentDiscrepancyUseCase;

    @Test
    void getDiscrepancy_상세와정렬된이력을민감정보없이반환한다() throws Exception {
        when(getPaymentDiscrepancyUseCase.get(USER_ID, 301L)).thenReturn(discrepancyDetail());

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/payment-discrepancies/301")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("결제 불일치 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.discrepancy.discrepancyId").value("301"))
            .andExpect(jsonPath("$.data.payment.paymentId").value("902"))
            .andExpect(jsonPath("$.data.payment.holdId").value("790"))
            .andExpect(jsonPath("$.data.payment.orderId").value("ORD-20260806-7H2P4X"))
            .andExpect(jsonPath("$.data.payment.portonePaymentId").value("portone-txn-abc123"))
            .andExpect(jsonPath("$.data.payment.status").value("DISCREPANT"))
            .andExpect(jsonPath("$.data.verifications[0].paymentVerificationId").value("551"))
            .andExpect(jsonPath("$.data.verifications[0].matched").value(false))
            .andExpect(jsonPath("$.data.verifications[1].paymentVerificationId").value("552"))
            .andExpect(jsonPath("$.data.actions").isArray())
            .andExpect(jsonPath("$.data.actions").isEmpty())
            .andExpect(jsonPath("$.data.verifications[0].responseHash").doesNotExist())
            .andExpect(jsonPath("$.data.verifications[0].internalDecision").doesNotExist())
            .andExpect(jsonPath("$.data.verifications[0].observedOrderId").doesNotExist());

        verify(getPaymentDiscrepancyUseCase).get(USER_ID, 301L);
    }

    @Test
    void getDiscrepancy_양의십진Long이아닌식별자는조회하지않고유형오류를반환한다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/platform-admin/payment-discrepancies/01")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(getPaymentDiscrepancyUseCase);
    }

    @Test
    void getDiscrepancy_대상이없으면계약된오류를반환한다() throws Exception {
        when(getPaymentDiscrepancyUseCase.get(USER_ID, 301L))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/payment-discrepancies/301")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getDiscrepancy_인증정보없음은조회하지않고미인증오류를반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/platform-admin/payment-discrepancies/301"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getPaymentDiscrepancyUseCase);
    }

    private PaymentDiscrepancyDetailInfo discrepancyDetail() {
        return new PaymentDiscrepancyDetailInfo(
            301L,
            "AMOUNT_MISMATCH",
            "OPEN",
            Instant.parse("2026-08-06T03:05:00Z"),
            902L,
            790L,
            "ORD-20260806-7H2P4X",
            "portone-txn-abc123",
            "DISCREPANT",
            15_000L,
            "KRW",
            List.of(
                new PaymentDiscrepancyDetailInfo.VerificationInfo(
                    551L,
                    "CONFIRM_REQUEST",
                    "PAID",
                    14_000L,
                    false,
                    Instant.parse("2026-08-06T03:05:00Z")
                ),
                new PaymentDiscrepancyDetailInfo.VerificationInfo(
                    552L,
                    "WEBHOOK",
                    "PAID",
                    14_000L,
                    false,
                    Instant.parse("2026-08-06T03:06:00Z")
                )
            ),
            List.of()
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) {
        return request.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, USER_ID));
    }
}
