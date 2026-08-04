package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.List;
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

        ReservationReadSnapshot snapshot = projections.get(0).toSnapshot();
        if (projections.stream().anyMatch(projection -> !sameReservation(snapshot, projection))) {
            throw new IllegalStateException("reservation number is not globally unique");
        }

        List<ReservationReadSnapshot.VisitInfo> visits = projections.stream()
            .map(ReservationReadProjection::toVisitInfo)
            .filter(visit -> visit != null)
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

    private boolean sameReservation(
        ReservationReadSnapshot snapshot,
        ReservationReadProjection projection
    ) {
        return snapshot.reservation() != null
            && Objects.equals(snapshot.reservation().reservationId(), projection.reservationId());
    }
}
