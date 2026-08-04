package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.SessionReservationReadProjection;

class SessionReservationReadServiceTest {

    private static final Long SESSION_ID = 2L;
    private static final Instant CHECKED_AT = Instant.parse("2026-08-04T01:23:45Z");

    @Test
    void 회차별_예약_조회_체크인된_예약과_소비_홀드가_정상이면_마스킹된_읽기_모델을_반환한다() {
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        SessionReservationReadService sessionReservationReadService = service(reservationRepository);
        when(reservationRepository.findSessionReservationReadProjections(SESSION_ID)).thenReturn(List.of(
            projection(ReservationStatus.CHECKED_IN, CapacityHoldStatus.CONSUMED, 17L, CHECKED_AT)
        ));

        List<SessionReservationReadResult> results = sessionReservationReadService.findBySessionId(SESSION_ID);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.reservationId()).isEqualTo(10L);
            assertThat(result.quantity()).isEqualTo(2);
            assertThat(result.participant()).isEqualTo(
                new ReservationParticipantMasker.MaskedParticipant("김*수", "010-****-5678")
            );
            assertThat(result.checkIn()).isEqualTo(
                new ReservationReadIntegrityValidator.CheckInInfo(true, CHECKED_AT)
            );
        });
    }

    @Test
    void 회차별_예약_조회_소비되지_않은_홀드가_연결되면_정합성_오류를_발생시킨다() {
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        SessionReservationReadService sessionReservationReadService = service(reservationRepository);
        when(reservationRepository.findSessionReservationReadProjections(SESSION_ID)).thenReturn(List.of(
            projection(ReservationStatus.CONFIRMED, CapacityHoldStatus.ACTIVE, null, null)
        ));

        assertThatThrownBy(() -> sessionReservationReadService.findBySessionId(SESSION_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("session reservation read data is inconsistent");
    }

    @Test
    void 회차별_예약_조회_홀드의_회차_연결이_다르면_정합성_오류를_발생시킨다() {
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        SessionReservationReadService sessionReservationReadService = service(reservationRepository);
        when(reservationRepository.findSessionReservationReadProjections(SESSION_ID)).thenReturn(List.of(
            projectionWithHoldSessionId(SESSION_ID + 1)
        ));

        assertThatThrownBy(() -> sessionReservationReadService.findBySessionId(SESSION_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("session reservation read data is inconsistent");
    }

    private SessionReservationReadService service(ReservationRepository reservationRepository) {
        return new SessionReservationReadService(
            reservationRepository,
            new ReservationReadIntegrityValidator(),
            new ReservationParticipantMasker()
        );
    }

    private SessionReservationReadProjection projection(
        ReservationStatus reservationStatus,
        CapacityHoldStatus holdStatus,
        Long visitId,
        Instant checkedAt
    ) {
        return new SessionReservationReadProjection(
            10L,
            "R20260804ABCDEFGHJKLM",
            reservationStatus,
            Instant.parse("2026-08-03T01:00:00Z"),
            1L,
            SESSION_ID,
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
            visitId == null ? null : SESSION_ID,
            visitId == null ? null : 3L,
            visitId == null ? null : 4L,
            checkedAt,
            5L,
            holdStatus,
            2,
            SESSION_ID,
            1L,
            10L
        );
    }

    private SessionReservationReadProjection projectionWithHoldSessionId(Long holdSessionId) {
        SessionReservationReadProjection projection = projection(
            ReservationStatus.CONFIRMED,
            CapacityHoldStatus.CONSUMED,
            null,
            null
        );
        return new SessionReservationReadProjection(
            projection.reservationId(),
            projection.reservationNo(),
            projection.reservationStatus(),
            projection.confirmedAt(),
            projection.reservationRegionId(),
            projection.sessionId(),
            projection.sessionStatus(),
            projection.startsAt(),
            projection.endsAt(),
            projection.checkinOpenAt(),
            projection.checkinCloseAt(),
            projection.sessionRegionId(),
            projection.contentId(),
            projection.contentTitle(),
            projection.contentRegionId(),
            projection.participantUserId(),
            projection.participantName(),
            projection.participantPhone(),
            projection.visitId(),
            projection.visitReservationId(),
            projection.visitRegionId(),
            projection.visitSessionId(),
            projection.visitContentId(),
            projection.visitParticipantUserId(),
            projection.checkedAt(),
            projection.holdId(),
            projection.holdStatus(),
            projection.holdQuantity(),
            holdSessionId,
            projection.holdRegionId(),
            projection.holdReservationId()
        );
    }
}
