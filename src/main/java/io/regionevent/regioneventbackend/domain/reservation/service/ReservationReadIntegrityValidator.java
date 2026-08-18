package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;

@Component
public class ReservationReadIntegrityValidator {

    public CheckInInfo validate(
        ReservationReadSnapshot snapshot,
        List<ReservationReadSnapshot.VisitInfo> visits
    ) {
        validateSnapshot(snapshot);
        validateParticipant(snapshot.participant());

        if (snapshot.reservation().status() != ReservationStatus.CHECKED_IN) {
            if (!visits.isEmpty()) {
                throw inconsistent();
            }
            return CheckInInfo.notCheckedIn();
        }

        if (visits.size() != 1) {
            throw inconsistent();
        }

        ReservationReadSnapshot.VisitInfo visit = visits.get(0);
        validateVisit(snapshot, visit);
        return CheckInInfo.checkedIn(visit.visitId(), visit.checkedAt());
    }

    private void validateSnapshot(ReservationReadSnapshot snapshot) {
        if (snapshot == null
            || snapshot.reservation() == null
            || snapshot.session() == null
            || snapshot.content() == null
            || snapshot.participant() == null) {
            throw inconsistent();
        }

        ReservationReadSnapshot.ReservationInfo reservation = snapshot.reservation();
        ReservationReadSnapshot.SessionInfo session = snapshot.session();
        ReservationReadSnapshot.ContentInfo content = snapshot.content();
        if (!hasValue(reservation.reservationId())
            || reservation.reservationNo() == null
            || reservation.reservationNo().isBlank()
            || reservation.status() == null
            || reservation.quantity() == null
            || reservation.quantity() <= 0
            || !hasValue(reservation.regionId())
            || !hasValue(session.sessionId())
            || session.status() == null
            || !hasValue(session.regionId())
            || !hasValue(content.contentId())
            || content.title() == null
            || content.title().isBlank()
            || !hasValue(content.regionId())
            || !sameId(reservation.regionId(), session.regionId())
            || !sameId(session.regionId(), content.regionId())) {
            throw inconsistent();
        }
    }

    private void validateParticipant(ReservationReadSnapshot.ParticipantInfo participant) {
        if (participant.userId() == null) {
            if (participant.name() != null || participant.phone() != null) {
                throw inconsistent();
            }
            return;
        }
        if (participant.name() == null
            || participant.name().isBlank()
            || participant.phone() == null
            || participant.phone().isBlank()) {
            throw inconsistent();
        }
    }

    private void validateVisit(
        ReservationReadSnapshot snapshot,
        ReservationReadSnapshot.VisitInfo visit
    ) {
        if (visit == null
            || !hasValue(visit.visitId())
            || visit.checkedAt() == null
            || !sameId(snapshot.reservation().reservationId(), visit.reservationId())
            || !sameId(snapshot.reservation().regionId(), visit.regionId())
            || !sameId(snapshot.session().sessionId(), visit.sessionId())
            || !sameId(snapshot.content().contentId(), visit.contentId())
            || !sameId(snapshot.participant().userId(), visit.participantUserId())) {
            throw inconsistent();
        }
    }

    private boolean hasValue(Long value) {
        return value != null;
    }

    private boolean sameId(Long expected, Long actual) {
        return Objects.equals(expected, actual);
    }

    private IllegalStateException inconsistent() {
        return new IllegalStateException("reservation read data is inconsistent");
    }

    public record CheckInInfo(
        Long visitId,
        boolean checkedIn,
        Instant checkedAt
    ) {

        private static CheckInInfo notCheckedIn() {
            return new CheckInInfo(null, false, null);
        }

        private static CheckInInfo checkedIn(Long visitId, Instant checkedAt) {
            return new CheckInInfo(visitId, true, checkedAt);
        }
    }
}
