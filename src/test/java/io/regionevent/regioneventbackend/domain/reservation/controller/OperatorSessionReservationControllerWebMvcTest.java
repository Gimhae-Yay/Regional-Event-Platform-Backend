package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.SessionReservationListResult;
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
class OperatorSessionReservationControllerWebMvcTest extends ReservationControllerWebMvcTestSupport {

    private static final long OPERATOR_ID = 100L;

    @Test
    void getSessionReservations_유효한요청_빈예약목록을포함한회차응답을반환한다() throws Exception {
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        when(getSessionReservationsUseCase.find(OPERATOR_ID, 1L, 2L)).thenReturn(new SessionReservationListResult(
            1L,
            new SessionReservationListResult.SessionInfo(
                2L,
                ContentSessionStatus.SCHEDULED,
                now,
                now.plusSeconds(3600),
                now,
                now.plusSeconds(1800)
            ),
            List.of()
        ));

        mockMvc.perform(authenticated(get("/api/v1/operator/contents/1/reservations").param("sessionId", "2")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("회차별 예약자 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(1))
            .andExpect(jsonPath("$.data.session.sessionId").value(2))
            .andExpect(jsonPath("$.data.reservations").isEmpty());

        verify(getSessionReservationsUseCase).find(OPERATOR_ID, 1L, 2L);
    }

    @Test
    void getSessionReservations_경로또는쿼리식별자오류_계약된오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/operator/contents/0/reservations").param("sessionId", "2")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/operator/contents/1/reservations")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/operator/contents/1/reservations").param("sessionId", "not-a-number")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(getSessionReservationsUseCase);
    }

    @Test
    void getSessionReservations_관할밖회차_찾을수없음오류를응답한다() throws Exception {
        when(getSessionReservationsUseCase.find(OPERATOR_ID, 1L, 2L))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/v1/operator/contents/1/reservations").param("sessionId", "2")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getSessionReservations_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/operator/contents/1/reservations").param("sessionId", "2"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

}
