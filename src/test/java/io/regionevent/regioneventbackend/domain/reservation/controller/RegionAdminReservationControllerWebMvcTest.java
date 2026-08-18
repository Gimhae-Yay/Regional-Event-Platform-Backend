package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.RegionAdminReservationSearchResult;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationParticipantMasker.MaskedParticipant;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationReadIntegrityValidator.CheckInInfo;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationReadSnapshot;
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
class RegionAdminReservationControllerWebMvcTest extends ReservationControllerWebMvcTestSupport {

    private static final long REGION_ADMIN_ID = 100L;

    @Test
    void search_유효한예약번호_지역관리자조회응답을반환한다() throws Exception {
        when(searchRegionAdminReservationByNumberUseCase.search(eq(REGION_ADMIN_ID), eq("R-2026"), any()))
            .thenReturn(regionAdminSearchResult());

        mockMvc.perform(authenticated(get("/api/v1/region-admin/reservations/search").param("reservationNo", "R-2026")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.reservationNo").value("R-2026"))
            .andExpect(jsonPath("$.data.participant.name").value("홍*동"))
            .andExpect(jsonPath("$.data.checkIn.canCheckIn").value(false));

        verify(searchRegionAdminReservationByNumberUseCase).search(eq(REGION_ADMIN_ID), eq("R-2026"), any());
    }

    @Test
    void search_파라미터없음또는관할밖예약_계약된오류를응답한다() throws Exception {
        when(searchRegionAdminReservationByNumberUseCase.search(eq(REGION_ADMIN_ID), eq(null), any()))
            .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));
        when(searchRegionAdminReservationByNumberUseCase.search(eq(REGION_ADMIN_ID), eq("missing"), any()))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/reservations/search")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/region-admin/reservations/search").param("reservationNo", "missing")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void search_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/reservations/search").param("reservationNo", "R-2026"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private RegionAdminReservationSearchResult regionAdminSearchResult() {
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        return new RegionAdminReservationSearchResult(
            new ReservationReadSnapshot.ReservationInfo(1L, "R-2026", ReservationStatus.CONFIRMED, now, null, null, null, 1, 10L),
            new ReservationReadSnapshot.SessionInfo(2L, ContentSessionStatus.SCHEDULED, now, now.plusSeconds(3600), now, now.plusSeconds(1800), 10L),
            new ReservationReadSnapshot.ContentInfo(3L, "김해 행사", 10L),
            new MaskedParticipant("홍*동", "010-****-5678"),
            new CheckInInfo(null, false, null)
        );
    }

}
