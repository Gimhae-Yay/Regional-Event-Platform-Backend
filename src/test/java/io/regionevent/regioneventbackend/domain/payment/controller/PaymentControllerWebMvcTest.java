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

import io.regionevent.regioneventbackend.domain.payment.dto.CreatePaymentResponse;
import io.regionevent.regioneventbackend.domain.payment.service.CreatePaymentUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class PaymentControllerWebMvcTest {

    private static final long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private CreatePaymentUseCase createPaymentUseCase;

    @Test
    void positiveAmountCreatesPendingPaymentResponse() throws Exception {
        when(createPaymentUseCase.create(eq(USER_ID), eq("10"), any(), eq("payment-key"), any())).thenReturn(
            new CreatePaymentResponse(
                true,
                new CreatePaymentResponse.PaymentResponse(
                    "20",
                    "10",
                    "ORD-1",
                    "PENDING",
                    new CreatePaymentResponse.AmountResponse(20000, 3000, 17000, "KRW"),
                    Instant.parse("2026-08-10T00:00:00Z")
                ),
                null
            )
        );

        mockMvc.perform(authenticated(post("/api/v1/me/reservation-holds/10/payments"))
                .header("Idempotency-Key", "payment-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"couponId\":\"7\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.requiresPayment").value(true))
            .andExpect(jsonPath("$.data.payment.paymentId").value("20"))
            .andExpect(jsonPath("$.data.payment.amount.finalAmount").value(17000))
            .andExpect(jsonPath("$.data.reservation").isEmpty());

        verify(createPaymentUseCase).create(eq(USER_ID), eq("10"), any(), eq("payment-key"), any());
    }

    @Test
    void zeroAmountReturnsConfirmedReservationResponse() throws Exception {
        when(createPaymentUseCase.create(eq(USER_ID), eq("10"), any(), eq("payment-key"), any())).thenReturn(
            new CreatePaymentResponse(
                false,
                null,
                new CreatePaymentResponse.ReservationResponse(
                    "30",
                    "R20260810ABC",
                    "10",
                    "CONFIRMED",
                    Instant.parse("2026-08-10T00:00:00Z")
                )
            )
        );

        mockMvc.perform(authenticated(post("/api/v1/me/reservation-holds/10/payments"))
                .header("Idempotency-Key", "payment-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.requiresPayment").value(false))
            .andExpect(jsonPath("$.data.payment").isEmpty())
            .andExpect(jsonPath("$.data.reservation.reservationId").value("30"));
    }

    @Test
    void numericCouponIdIsRejectedAsInvalidType() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/me/reservation-holds/10/payments"))
                .header("Idempotency-Key", "payment-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"couponId\":7}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(createPaymentUseCase);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) {
        return request.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, USER_ID));
    }
}
