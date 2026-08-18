package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.SessionReservationReadProjection;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class SessionReservationReadService {

    private final ReservationRepository reservationRepository;
    private final ReservationReadIntegrityValidator reservationReadIntegrityValidator;
    private final ReservationParticipantMasker reservationParticipantMasker;

    public SessionReservationReadService(
        ReservationRepository reservationRepository,
        ReservationReadIntegrityValidator reservationReadIntegrityValidator,
        ReservationParticipantMasker reservationParticipantMasker
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationReadIntegrityValidator = reservationReadIntegrityValidator;
        this.reservationParticipantMasker = reservationParticipantMasker;
    }

    @Transactional(readOnly = true)
    public List<SessionReservationReadResult> findBySessionId(Long sessionId) {
        validateSessionId(sessionId);

        Map<Long, List<SessionReservationReadProjection>> projectionsByReservationId = new LinkedHashMap<>();
        reservationRepository.findSessionReservationReadProjections(sessionId)
            .forEach(projection -> projectionsByReservationId
                .computeIfAbsent(projection.reservationId(), ignored -> new ArrayList<>())
                .add(projection));

        return projectionsByReservationId.values().stream()
            .map(this::toResult)
            .toList();
    }

    private SessionReservationReadResult toResult(List<SessionReservationReadProjection> projections) {
        if (projections.isEmpty()) {
            throw inconsistent();
        }

        ReservationReadSnapshot snapshot = toSnapshot(projections.get(0));
        if (projections.stream().anyMatch(projection -> !snapshot.equals(toSnapshot(projection)))) {
            throw inconsistent();
        }
        projections.forEach(projection -> validateCapacityHold(snapshot, projection));

        List<ReservationReadSnapshot.VisitInfo> visits = projections.stream()
            .map(this::toVisitInfo)
            .filter(Objects::nonNull)
            .toList();
        ReservationReadIntegrityValidator.CheckInInfo checkIn = reservationReadIntegrityValidator.validate(
            snapshot,
            visits
        );
        ReservationParticipantMasker.MaskedParticipant participant = reservationParticipantMasker.mask(
            snapshot.participant()
        );
        return new SessionReservationReadResult(
            snapshot.reservation().reservationId(),
            snapshot.reservation().reservationNo(),
            snapshot.reservation().status(),
            projections.get(0).holdQuantity(),
            snapshot.reservation().confirmedAt(),
            participant,
            checkIn
        );
    }

    private ReservationReadSnapshot toSnapshot(SessionReservationReadProjection projection) {
        return new ReservationReadSnapshot(
            new ReservationReadSnapshot.ReservationInfo(
                projection.reservationId(),
                projection.reservationNo(),
                projection.reservationStatus(),
                projection.confirmedAt(),
                null,
                null,
                null,
                projection.holdQuantity(),
                projection.reservationRegionId()
            ),
            new ReservationReadSnapshot.SessionInfo(
                projection.sessionId(),
                projection.sessionStatus(),
                projection.startsAt(),
                projection.endsAt(),
                projection.checkinOpenAt(),
                projection.checkinCloseAt(),
                projection.sessionRegionId()
            ),
            new ReservationReadSnapshot.ContentInfo(
                projection.contentId(),
                projection.contentTitle(),
                projection.contentRegionId()
            ),
            new ReservationReadSnapshot.ParticipantInfo(
                projection.participantUserId(),
                projection.participantName(),
                projection.participantPhone()
            )
        );
    }

    private ReservationReadSnapshot.VisitInfo toVisitInfo(SessionReservationReadProjection projection) {
        if (projection.visitId() == null) {
            return null;
        }
        return new ReservationReadSnapshot.VisitInfo(
            projection.visitId(),
            projection.visitReservationId(),
            projection.visitRegionId(),
            projection.visitSessionId(),
            projection.visitContentId(),
            projection.visitParticipantUserId(),
            projection.checkedAt()
        );
    }

    private void validateCapacityHold(
        ReservationReadSnapshot snapshot,
        SessionReservationReadProjection projection
    ) {
        if (projection.holdId() == null
            || projection.holdStatus() != CapacityHoldStatus.CONSUMED
            || projection.holdQuantity() == null
            || projection.holdQuantity() <= 0
            || !sameId(snapshot.reservation().reservationId(), projection.holdReservationId())
            || !sameId(snapshot.reservation().regionId(), projection.holdRegionId())
            || !sameId(snapshot.session().sessionId(), projection.holdSessionId())) {
            throw inconsistent();
        }
    }

    private void validateSessionId(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean sameId(Long expected, Long actual) {
        return Objects.equals(expected, actual);
    }

    private IllegalStateException inconsistent() {
        return new IllegalStateException("session reservation read data is inconsistent");
    }
}
