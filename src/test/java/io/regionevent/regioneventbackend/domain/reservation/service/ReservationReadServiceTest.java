package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationReadProjection;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ReservationReadServiceTest {

    private static final String RESERVATION_NO = "R20260804ABCDEFGHJKLM";
    private static final Instant CHECKED_AT = Instant.parse("2026-08-04T01:23:45Z");

    @Test
    void findByReservationNo_whenCheckedInReservationExists_returnsReadModel() {
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        ReservationReadService reservationReadService = service(reservationRepository);
        when(reservationRepository.findReadProjectionsByReservationNo(RESERVATION_NO)).thenReturn(List.of(
            projection(ReservationStatus.CHECKED_IN, 17L, CHECKED_AT)
        ));

        ReservationReadResult result = reservationReadService.findByReservationNo(RESERVATION_NO);

        assertThat(result.snapshot().reservation().reservationId()).isEqualTo(10L);
        assertThat(result.snapshot().content().title()).isEqualTo("김해 가야문화 체험");
        assertThat(result.snapshot().participant().name()).isEqualTo("김민수");
        assertThat(result.checkIn()).isEqualTo(
            new ReservationReadIntegrityValidator.CheckInInfo(true, CHECKED_AT)
        );
    }

    @Test
    void findByReservationNo_whenReservationNoIsBlank_throwsInvalidInputException() {
        ReservationReadService reservationReadService = service(mock(ReservationRepository.class));

        assertThatThrownBy(() -> reservationReadService.findByReservationNo(" "))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
    }

    @Test
    void findByReservationNo_whenReservationDoesNotExist_throwsNotFoundException() {
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        ReservationReadService reservationReadService = service(reservationRepository);
        when(reservationRepository.findReadProjectionsByReservationNo(RESERVATION_NO)).thenReturn(List.of());

        assertThatThrownBy(() -> reservationReadService.findByReservationNo(RESERVATION_NO))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );
    }

    @Test
    void findByReservationNo_whenCheckedInReservationHasNoVisit_throwsConsistencyException() {
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        ReservationReadService reservationReadService = service(reservationRepository);
        when(reservationRepository.findReadProjectionsByReservationNo(RESERVATION_NO)).thenReturn(List.of(
            projection(ReservationStatus.CHECKED_IN, null, null)
        ));

        assertThatThrownBy(() -> reservationReadService.findByReservationNo(RESERVATION_NO))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("reservation read data is inconsistent");
    }

    private ReservationReadService service(ReservationRepository reservationRepository) {
        return new ReservationReadService(
            reservationRepository,
            new ReservationReadIntegrityValidator()
        );
    }

    private ReservationReadProjection projection(
        ReservationStatus reservationStatus,
        Long visitId,
        Instant checkedAt
    ) {
        return new ReservationReadProjection(
            10L,
            RESERVATION_NO,
            reservationStatus,
            Instant.parse("2026-08-03T01:00:00Z"),
            null,
            null,
            null,
            1L,
            2L,
            ContentSessionStatus.SCHEDULED,
            Instant.parse("2026-08-04T01:00:00Z"),
            Instant.parse("2026-08-04T03:00:00Z"),
            Instant.parse("2026-08-04T00:30:00Z"),
            Instant.parse("2026-08-04T02:30:00Z"),
            1L,
            3L,
            "김해 가야문화 체험",
            1L,
            4L,
            "김민수",
            "01012345678",
            visitId,
            visitId == null ? null : 10L,
            visitId == null ? null : 1L,
            visitId == null ? null : 2L,
            visitId == null ? null : 3L,
            visitId == null ? null : 4L,
            checkedAt
        );
    }
}
