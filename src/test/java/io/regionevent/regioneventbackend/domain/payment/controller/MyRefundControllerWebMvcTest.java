package io.regionevent.regioneventbackend.domain.payment.controller;

import static org.mockito.Mockito.mock;
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

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.service.GetMyRefundUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.GetMyRefundsUseCase;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest({MyRefundController.class, MyRefundDetailController.class})
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class MyRefundControllerWebMvcTest {

    private static final long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetMyRefundUseCase getMyRefundUseCase;

    @MockitoBean
    private GetMyRefundsUseCase getMyRefundsUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void getMyRefunds_returnsContractFieldsInUseCaseOrder() throws Exception {
        Refund succeededRefund = refund(
            20L,
            30L,
            40L,
            17_000L,
            RefundStatus.SUCCEEDED,
            Instant.parse("2026-08-07T01:00:00Z"),
            Instant.parse("2026-08-07T01:00:12Z")
        );
        Refund requestedRefund = refund(
            10L,
            11L,
            12L,
            5_000L,
            RefundStatus.REQUESTED,
            Instant.parse("2026-08-06T01:00:00Z"),
            null
        );
        when(getMyRefundsUseCase.findAll(USER_ID)).thenReturn(List.of(succeededRefund, requestedRefund));

        mockMvc.perform(authenticated(get("/api/v1/me/refunds")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.refunds[0].refundId").value("20"))
            .andExpect(jsonPath("$.data.refunds[0].paymentId").value("30"))
            .andExpect(jsonPath("$.data.refunds[0].reservationId").value("40"))
            .andExpect(jsonPath("$.data.refunds[0].amount").value(17_000))
            .andExpect(jsonPath("$.data.refunds[0].currency").value("KRW"))
            .andExpect(jsonPath("$.data.refunds[0].status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.refunds[0].completedAt").value("2026-08-07T01:00:12Z"))
            .andExpect(jsonPath("$.data.refunds[1].refundId").value("10"))
            .andExpect(jsonPath("$.data.refunds[1].completedAt").isEmpty())
            .andExpect(jsonPath("$.data.refunds[0].paymentMethod").doesNotExist())
            .andExpect(jsonPath("$.data.refunds[0].rawPayload").doesNotExist());

        verify(getMyRefundsUseCase).findAll(USER_ID);
    }

    @Test
    void getMyRefunds_whenNoRefunds_returnsEmptyArray() throws Exception {
        when(getMyRefundsUseCase.findAll(USER_ID)).thenReturn(List.of());

        mockMvc.perform(authenticated(get("/api/v1/me/refunds")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.refunds").isArray())
            .andExpect(jsonPath("$.data.refunds").isEmpty());

        verify(getMyRefundsUseCase).findAll(USER_ID);
    }

    @Test
    void getMyRefunds_withoutAuthentication_returnsUnauthenticatedWithoutReadingRefunds() throws Exception {
        mockMvc.perform(get("/api/v1/me/refunds"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getMyRefundsUseCase);
    }

    @Test
    void get_returnsOnlySpecifiedRefundFields() throws Exception {
        Refund refund = refund(
            10L,
            20L,
            30L,
            17_000L,
            RefundStatus.PROCESSING,
            Instant.parse("2026-08-07T01:00:00Z"),
            null
        );
        when(getMyRefundUseCase.find(USER_ID, 10L)).thenReturn(refund);

        mockMvc.perform(authenticated(get("/api/v1/me/refunds/10")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.refundId").value("10"))
            .andExpect(jsonPath("$.data.paymentId").value("20"))
            .andExpect(jsonPath("$.data.reservationId").value("30"))
            .andExpect(jsonPath("$.data.amount").value(17_000))
            .andExpect(jsonPath("$.data.currency").value("KRW"))
            .andExpect(jsonPath("$.data.status").value("PROCESSING"))
            .andExpect(jsonPath("$.data.requestedAt").value("2026-08-07T01:00:00Z"))
            .andExpect(jsonPath("$.data.completedAt").isEmpty())
            .andExpect(jsonPath("$.data.paymentMethod").doesNotExist())
            .andExpect(jsonPath("$.data.rawPayload").doesNotExist());

        verify(getMyRefundUseCase).find(USER_ID, 10L);
    }

    @Test
    void get_whenRefundIdIsNotPositive_returnsInvalidTypeWithoutReadingRefund() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/me/refunds/0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(getMyRefundUseCase);
    }

    @Test
    void get_whenRefundIdIsNotLong_returnsInvalidTypeWithoutReadingRefund() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/me/refunds/9223372036854775808")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(getMyRefundUseCase);
    }

    @Test
    void get_whenRefundIsMissingOrNotOwned_returnsNotFound() throws Exception {
        when(getMyRefundUseCase.find(USER_ID, 10L)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/v1/me/refunds/10")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(getMyRefundUseCase).find(USER_ID, 10L);
    }

    @Test
    void get_withoutAuthentication_returnsUnauthenticatedWithoutReadingRefund() throws Exception {
        mockMvc.perform(get("/api/v1/me/refunds/10"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getMyRefundUseCase);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) {
        return request.header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(USER_ID));
    }

    private Refund refund(
        Long refundId,
        Long paymentId,
        Long reservationId,
        long amount,
        RefundStatus status,
        Instant requestedAt,
        Instant completedAt
    ) {
        Reservation reservation = mock(Reservation.class);
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        Payment payment = mock(Payment.class);
        Refund refund = mock(Refund.class);
        when(reservation.getReservationId()).thenReturn(reservationId);
        when(snapshot.getCurrency()).thenReturn("KRW");
        when(payment.getPaymentId()).thenReturn(paymentId);
        when(payment.getReservation()).thenReturn(reservation);
        when(payment.getReservationPriceSnapshot()).thenReturn(snapshot);
        when(refund.getRefundId()).thenReturn(refundId);
        when(refund.getPayment()).thenReturn(payment);
        when(refund.getAmount()).thenReturn(amount);
        when(refund.getStatus()).thenReturn(status);
        when(refund.getRequestedAt()).thenReturn(requestedAt);
        when(refund.getCompletedAt()).thenReturn(completedAt);
        return refund;
    }
}
