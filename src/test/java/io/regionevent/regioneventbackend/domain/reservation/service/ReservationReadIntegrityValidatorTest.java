package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;

class ReservationReadIntegrityValidatorTest {

    private static final Instant CHECKED_AT = Instant.parse("2026-08-04T01:23:45Z");

    private final ReservationReadIntegrityValidator validator = new ReservationReadIntegrityValidator();

    @Test
    void validate_whenCheckedInReservationHasMatchingVisit_returnsCheckedInInformation() {
        ReservationReadSnapshot snapshot = snapshot(ReservationStatus.CHECKED_IN, 4L);
        ReservationReadSnapshot.VisitInfo visit = matchingVisit(4L);

        ReservationReadIntegrityValidator.CheckInInfo checkIn = validator.validate(snapshot, List.of(visit));

        assertThat(checkIn).isEqualTo(
            new ReservationReadIntegrityValidator.CheckInInfo(17L, true, CHECKED_AT)
        );
    }

    @Test
    void validate_whenUncheckedReservationHasNoVisit_returnsUncheckedInformation() {
        for (ReservationStatus status : List.of(
            ReservationStatus.CONFIRMED,
            ReservationStatus.CANCELLED,
            ReservationStatus.EXPIRED
        )) {
            ReservationReadIntegrityValidator.CheckInInfo checkIn = validator.validate(
                snapshot(status, 4L),
                List.of()
            );

            assertThat(checkIn).isEqualTo(
                new ReservationReadIntegrityValidator.CheckInInfo(null, false, null)
            );
        }
    }

    @Test
    void validate_whenCheckedInReservationHasNoVisit_throwsConsistencyException() {
        assertThatThrownBy(() -> validator.validate(
            snapshot(ReservationStatus.CHECKED_IN, 4L),
            List.of()
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("reservation read data is inconsistent");
    }

    @Test
    void validate_whenVisitContentDoesNotMatchReservation_throwsConsistencyException() {
        ReservationReadSnapshot.VisitInfo mismatchedVisit = new ReservationReadSnapshot.VisitInfo(
            17L,
            10L,
            1L,
            2L,
            999L,
            4L,
            CHECKED_AT
        );

        assertThatThrownBy(() -> validator.validate(
            snapshot(ReservationStatus.CHECKED_IN, 4L),
            List.of(mismatchedVisit)
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("reservation read data is inconsistent");
    }

    @Test
    void validate_whenUncheckedReservationHasVisit_throwsConsistencyException() {
        assertThatThrownBy(() -> validator.validate(
            snapshot(ReservationStatus.CONFIRMED, 4L),
            List.of(matchingVisit(4L))
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("reservation read data is inconsistent");
    }

    @Test
    void validate_whenWithdrawnParticipantStillHasVisitUserLink_throwsConsistencyException() {
        assertThatThrownBy(() -> validator.validate(
            snapshot(ReservationStatus.CHECKED_IN, null),
            List.of(matchingVisit(4L))
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("reservation read data is inconsistent");
    }

    private ReservationReadSnapshot snapshot(ReservationStatus reservationStatus, Long participantUserId) {
        return new ReservationReadSnapshot(
            new ReservationReadSnapshot.ReservationInfo(
                10L,
                "R20260804ABCDEFGHJKLM",
                reservationStatus,
                Instant.parse("2026-08-03T01:00:00Z"),
                null,
                null,
                null,
                1,
                1L
            ),
            new ReservationReadSnapshot.SessionInfo(
                2L,
                ContentSessionStatus.SCHEDULED,
                Instant.parse("2026-08-04T01:00:00Z"),
                Instant.parse("2026-08-04T03:00:00Z"),
                Instant.parse("2026-08-04T00:30:00Z"),
                Instant.parse("2026-08-04T02:30:00Z"),
                1L
            ),
            new ReservationReadSnapshot.ContentInfo(3L, "김해 가야문화 체험", 1L),
            new ReservationReadSnapshot.ParticipantInfo(
                participantUserId,
                participantUserId == null ? null : "김민수",
                participantUserId == null ? null : "01012345678"
            )
        );
    }

    private ReservationReadSnapshot.VisitInfo matchingVisit(Long participantUserId) {
        return new ReservationReadSnapshot.VisitInfo(
            17L,
            10L,
            1L,
            2L,
            3L,
            participantUserId,
            CHECKED_AT
        );
    }
}
