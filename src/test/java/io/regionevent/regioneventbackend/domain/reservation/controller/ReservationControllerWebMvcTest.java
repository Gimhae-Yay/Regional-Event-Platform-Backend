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
import org.springframework.http.MediaType;

import io.regionevent.regioneventbackend.domain.reservation.dto.ConfirmReservationResponse;
import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationHoldResponse;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationConfirmationResult;
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
class ReservationControllerWebMvcTest extends ReservationControllerWebMvcTestSupport {

    @Test
    void createReservationHold_유효한요청_홀드생성응답을반환한다() throws Exception {
        when(createReservationHoldUseCase.create(eq(AUTHENTICATED_USER_ID), any())).thenReturn(new CreateReservationHoldResponse(
            "1", "10", 2, "ACTIVE", Instant.parse("2026-08-05T00:10:00Z"), Instant.parse("2026-08-05T00:00:00Z")
        ));

        mockMvc.perform(authenticated(post("/api/v1/reservations"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"10\",\"quantity\":2}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("예약 대기 및 정원 홀드 생성에 성공했습니다."))
            .andExpect(jsonPath("$.data.holdId").value("1"))
            .andExpect(jsonPath("$.data.sessionId").value("10"))
            .andExpect(jsonPath("$.data.quantity").value(2))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(createReservationHoldUseCase).create(eq(AUTHENTICATED_USER_ID), any());
    }

    @Test
    void createReservationHold_요청형식오류_입력오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/reservations"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"\",\"quantity\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(post("/api/v1/reservations"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"10\",\"quantity\":\"two\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(createReservationHoldUseCase);
    }

    @Test
    void createReservationHold_정원부족_충돌오류를응답한다() throws Exception {
        when(createReservationHoldUseCase.create(eq(AUTHENTICATED_USER_ID), any()))
            .thenThrow(new BusinessException(ErrorCode.RESERVATION_HOLD_CONFLICT));

        mockMvc.perform(authenticated(post("/api/v1/reservations"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"10\",\"quantity\":1}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_HOLD_CONFLICT"));
    }

    @Test
    void confirmReservation_유효한멱등키_예약확정응답을반환한다() throws Exception {
        when(reservationConfirmationUseCase.confirm(eq(AUTHENTICATED_USER_ID), eq("1"), eq("key"), any())).thenReturn(
            ReservationConfirmationResult.success(new ConfirmReservationResponse(
                "20", "R-2026", "1", "10", "CONFIRMED", Instant.parse("2026-08-05T00:00:00Z")
            ))
        );

        mockMvc.perform(authenticated(post("/api/v1/reservation-holds/1/confirm").header("Idempotency-Key", "key")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("무료 예약 확정에 성공했습니다."))
            .andExpect(jsonPath("$.data.reservationId").value("20"))
            .andExpect(jsonPath("$.data.reservationNo").value("R-2026"));

        verify(reservationConfirmationUseCase).confirm(eq(AUTHENTICATED_USER_ID), eq("1"), eq("key"), any());
    }

    @Test
    void confirmReservation_멱등키누락또는확정불가_계약된오류를응답한다() throws Exception {
        when(reservationConfirmationUseCase.confirm(eq(AUTHENTICATED_USER_ID), eq("1"), eq(null), any()))
            .thenReturn(ReservationConfirmationResult.failure(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));

        mockMvc.perform(authenticated(post("/api/v1/reservation-holds/1/confirm")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void reservation_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"10\",\"quantity\":1}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

}
