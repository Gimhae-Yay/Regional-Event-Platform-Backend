package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import io.regionevent.regioneventbackend.domain.reservation.service.MyReservationQrResult;
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
class MyReservationQrControllerWebMvcTest extends ReservationControllerWebMvcTestSupport {

    private static final long USER_ID = 100L;

    @Test
    void get_발급가능한예약_QR응답과캐시금지를반환한다() throws Exception {
        when(getMyReservationQrUseCase.get(USER_ID, 1L)).thenReturn(new MyReservationQrResult(
            1L,
            10L,
            "qr-token",
            Instant.parse("2026-08-05T00:00:00Z"),
            Instant.parse("2026-08-05T00:05:00Z"),
            Instant.parse("2026-08-05T00:10:00Z")
        ));

        mockMvc.perform(authenticated(get("/api/v1/me/reservations/1/qr")))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("예약 QR 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.reservationId").value(1))
            .andExpect(jsonPath("$.data.sessionId").value(10))
            .andExpect(jsonPath("$.data.qrToken").value("qr-token"));

        verify(getMyReservationQrUseCase).get(USER_ID, 1L);
    }

    @Test
    void get_식별자가유효하지않음_입력또는타입오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/me/reservations/01/qr")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/me/reservations/not-a-number/qr")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(getMyReservationQrUseCase);
    }

    @Test
    void get_예약발급불가_계약된업무오류를응답한다() throws Exception {
        when(getMyReservationQrUseCase.get(USER_ID, 1L)).thenThrow(new BusinessException(ErrorCode.QR_ISSUE_CONFLICT));

        mockMvc.perform(authenticated(get("/api/v1/me/reservations/1/qr")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("QR_ISSUE_CONFLICT"));
    }

    @Test
    void get_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/me/reservations/1/qr"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

}
