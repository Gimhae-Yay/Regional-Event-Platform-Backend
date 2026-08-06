package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import io.regionevent.regioneventbackend.domain.reservation.dto.CancelReservationResponse;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@WebMvcTest({
    MyReservationQrController.class,
    OperatorReservationController.class,
    OperatorSessionReservationController.class,
    RegionAdminReservationController.class,
    MyReservationController.class,
    ReservationController.class,
    ReservationHoldController.class
})
class ReservationCancellationControllerWebMvcTest extends ReservationControllerWebMvcTestSupport {

    private static final long USER_ID = 100L;

    @Test
    void cancelReservation_취소가능한예약_취소응답을반환한다() throws Exception {
        when(reservationCancellationUseCase.cancel(eq(USER_ID), eq(1L), any())).thenReturn(new CancelReservationResponse(
            "1", "10", "CANCELLED", "USER_REQUEST", Instant.parse("2026-08-05T00:00:00Z"), Instant.parse("2026-08-05T00:00:00Z")
        ));

        mockMvc.perform(authenticated(post("/api/v1/me/reservations/1/cancel")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("예약 취소에 성공했습니다."))
            .andExpect(jsonPath("$.data.reservationId").value("1"))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        verify(reservationCancellationUseCase).cancel(eq(USER_ID), eq(1L), any());
    }

    @Test
    void cancelReservation_식별자오류_입력또는타입오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/me/reservations/0/cancel")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(post("/api/v1/me/reservations/not-a-number/cancel")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(reservationCancellationUseCase);
    }

    @Test
    void cancelReservation_취소불가_충돌오류를응답한다() throws Exception {
        when(reservationCancellationUseCase.cancel(eq(USER_ID), eq(1L), any()))
            .thenThrow(new BusinessException(ErrorCode.RESERVATION_CANCEL_CONFLICT));

        mockMvc.perform(authenticated(post("/api/v1/me/reservations/1/cancel")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_CANCEL_CONFLICT"));
    }

    @Test
    void cancelReservation_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/me/reservations/1/cancel"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

}
