package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationReadProjection;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ReservationReadService {

    private final ReservationRepository reservationRepository;
    private final ReservationReadIntegrityValidator reservationReadIntegrityValidator;

    public ReservationReadService(
        ReservationRepository reservationRepository,
        ReservationReadIntegrityValidator reservationReadIntegrityValidator
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationReadIntegrityValidator = reservationReadIntegrityValidator;
    }

    @Transactional(readOnly = true)
    public ReservationReadResult findByReservationNo(String reservationNo) {
        validateReservationNo(reservationNo);

        List<ReservationReadProjection> projections = reservationRepository
            .findReadProjectionsByReservationNo(reservationNo);
        if (projections.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        return toReadResult(projections);
    }

    @Transactional(readOnly = true)
    public ReservationReadResult findOwnedByReservationId(Long userId, Long reservationId) {
        validateId(userId);
        validateId(reservationId);

        List<ReservationReadProjection> projections = reservationRepository
            .findReadProjectionsByReservationId(reservationId);
        if (projections.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!Objects.equals(projections.get(0).participantUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return toReadResult(projections);
    }

    @Transactional(readOnly = true)
    public ReservationReadResult findByReservationId(Long reservationId) {
        validateId(reservationId);

        List<ReservationReadProjection> projections = reservationRepository
            .findReadProjectionsByReservationId(reservationId);
        if (projections.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        return toReadResult(projections);
    }

    @Transactional(readOnly = true)
    public List<ReservationReadResult> findAllOwnedByUserId(Long userId) {
        validateId(userId);

        Map<Long, List<ReservationReadProjection>> projectionsByReservationId = new LinkedHashMap<>();
        reservationRepository.findReadProjectionsByUserId(userId)
            .forEach(projection -> projectionsByReservationId
                .computeIfAbsent(projection.reservationId(), ignored -> new ArrayList<>())
                .add(projection));

        return projectionsByReservationId.values().stream()
            .map(this::toReadResult)
            .toList();
    }

    private ReservationReadResult toReadResult(List<ReservationReadProjection> projections) {
        ReservationReadSnapshot snapshot = toSnapshot(projections.get(0));
        if (projections.stream().anyMatch(projection -> !sameReservation(snapshot, projection))) {
            throw new IllegalStateException("reservation read data is inconsistent");
        }

        List<ReservationReadSnapshot.VisitInfo> visits = projections.stream()
            .map(this::toVisitInfo)
            .filter(Objects::nonNull)
            .toList();
        ReservationReadIntegrityValidator.CheckInInfo checkIn = reservationReadIntegrityValidator.validate(
            snapshot,
            visits
        );
        return new ReservationReadResult(snapshot, checkIn);
    }

    private void validateReservationNo(String reservationNo) {
        if (reservationNo == null || reservationNo.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean sameReservation(
        ReservationReadSnapshot snapshot,
        ReservationReadProjection projection
    ) {
        return snapshot.reservation() != null
            && Objects.equals(snapshot.reservation().reservationId(), projection.reservationId());
    }

    private ReservationReadSnapshot toSnapshot(ReservationReadProjection projection) {
        return new ReservationReadSnapshot(
            new ReservationReadSnapshot.ReservationInfo(
                projection.reservationId(),
                projection.reservationNo(),
                projection.reservationStatus(),
                projection.confirmedAt(),
                projection.cancelledAt(),
                projection.cancellationReason(),
                projection.expiredAt(),
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

    private ReservationReadSnapshot.VisitInfo toVisitInfo(ReservationReadProjection projection) {
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
}
