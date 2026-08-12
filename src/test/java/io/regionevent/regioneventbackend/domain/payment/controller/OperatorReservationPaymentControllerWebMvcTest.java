package io.regionevent.regioneventbackend.domain.payment.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.service.GetOperatorReservationPaymentUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.OperatorReservationPaymentInfo;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(OperatorReservationPaymentController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class OperatorReservationPaymentControllerWebMvcTest {

    private static final long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetOperatorReservationPaymentUseCase getOperatorReservationPaymentUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void get_returnsPaymentRefundAndDiscrepancyFields() throws Exception {
        when(getOperatorReservationPaymentUseCase.get(USER_ID, 10L)).thenReturn(paidReservation());

        mockMvc.perform(authenticated(get("/api/v1/operator/reservations/10/payment")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("담당 예약 결제·환불 상태 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.reservationId").value("10"))
            .andExpect(jsonPath("$.data.payment.paymentId").value("20"))
            .andExpect(jsonPath("$.data.payment.discrepancy.status").value("REFUND_REQUESTED"))
            .andExpect(jsonPath("$.data.refund.refundId").value("30"))
            .andExpect(jsonPath("$.data.updatedAt").value("2026-08-12T05:00:00Z"));

        verify(getOperatorReservationPaymentUseCase).get(USER_ID, 10L);
    }

    @Test
    void get_whenFreeReservation_returnsNullPaymentAndRefund() throws Exception {
        when(getOperatorReservationPaymentUseCase.get(USER_ID, 10L)).thenReturn(new OperatorReservationPaymentInfo(
            10L,
            "R20260812TEST",
            11L,
            12L,
            null,
            null,
            Instant.parse("2026-08-12T01:00:00Z")
        ));

        mockMvc.perform(authenticated(get("/api/v1/operator/reservations/10/payment")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.payment").isEmpty())
            .andExpect(jsonPath("$.data.refund").isEmpty())
            .andExpect(jsonPath("$.data.updatedAt").value("2026-08-12T01:00:00Z"));
    }

    @Test
    void get_whenReservationIdIsNotPositive_returnsInvalidInputWithoutReadingPayment() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/operator/reservations/0/payment")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(getOperatorReservationPaymentUseCase);
    }

    @Test
    void get_withoutAuthentication_returnsUnauthenticatedWithoutReadingPayment() throws Exception {
        mockMvc.perform(get("/api/v1/operator/reservations/10/payment"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getOperatorReservationPaymentUseCase);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) {
        return request.header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(USER_ID));
    }

    private OperatorReservationPaymentInfo paidReservation() {
        return new OperatorReservationPaymentInfo(
            10L,
            "R20260812TEST",
            11L,
            12L,
            new OperatorReservationPaymentInfo.PaymentInfo(
                20L,
                PaymentStatus.APPROVED,
                17_000L,
                "KRW",
                new OperatorReservationPaymentInfo.DiscrepancyInfo(40L, "REFUND_REQUESTED")
            ),
            new OperatorReservationPaymentInfo.RefundInfo(
                30L,
                RefundStatus.PROCESSING,
                17_000L,
                Instant.parse("2026-08-12T03:00:00Z"),
                null
            ),
            Instant.parse("2026-08-12T05:00:00Z")
        );
    }
}
