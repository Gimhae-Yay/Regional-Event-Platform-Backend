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

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.service.GetMyPaymentUseCase;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(MyPaymentController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class MyPaymentControllerWebMvcTest {

    private static final long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetMyPaymentUseCase getMyPaymentUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void get_returnsOnlySpecifiedPaymentFields() throws Exception {
        Payment payment = pendingPayment();
        when(getMyPaymentUseCase.find(USER_ID, 10L)).thenReturn(payment);

        mockMvc.perform(authenticated(get("/api/v1/me/payments/10")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.paymentId").value("10"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.amount.finalAmount").value(17000))
            .andExpect(jsonPath("$.data.reservationId").isEmpty())
            .andExpect(jsonPath("$.data.finalizedAt").isEmpty())
            .andExpect(jsonPath("$.data.portonePaymentId").doesNotExist())
            .andExpect(jsonPath("$.data.paymentMethod").doesNotExist())
            .andExpect(jsonPath("$.data.rawPayload").doesNotExist());

        verify(getMyPaymentUseCase).find(USER_ID, 10L);
    }

    @Test
    void get_whenPaymentIdIsNotPositive_returnsInvalidInputWithoutReadingPayment() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/me/payments/0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(getMyPaymentUseCase);
    }

    @Test
    void get_whenPaymentIdIsNotLong_returnsInvalidTypeWithoutReadingPayment() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/me/payments/9223372036854775808")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(getMyPaymentUseCase);
    }

    @Test
    void get_withoutAuthentication_returnsUnauthenticatedWithoutReadingPayment() throws Exception {
        mockMvc.perform(get("/api/v1/me/payments/10"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getMyPaymentUseCase);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) {
        return request.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, USER_ID));
    }

    private Payment pendingPayment() {
        CapacityHold capacityHold = org.mockito.Mockito.mock(CapacityHold.class);
        ReservationPriceSnapshot snapshot = org.mockito.Mockito.mock(ReservationPriceSnapshot.class);
        Payment payment = org.mockito.Mockito.mock(Payment.class);
        when(capacityHold.getHoldId()).thenReturn(7L);
        when(snapshot.getBaseAmount()).thenReturn(20_000L);
        when(snapshot.getDiscountAmount()).thenReturn(3_000L);
        when(snapshot.getFinalAmount()).thenReturn(17_000L);
        when(snapshot.getCurrency()).thenReturn("KRW");
        when(payment.getPaymentId()).thenReturn(10L);
        when(payment.getCapacityHold()).thenReturn(capacityHold);
        when(payment.getReservationPriceSnapshot()).thenReturn(snapshot);
        when(payment.getOrderId()).thenReturn("ORDER-10");
        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);
        when(payment.getCreatedAt()).thenReturn(Instant.parse("2026-08-11T00:00:00Z"));
        return payment;
    }
}
