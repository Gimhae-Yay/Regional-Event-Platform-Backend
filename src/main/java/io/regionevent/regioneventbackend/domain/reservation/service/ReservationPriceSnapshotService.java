package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationPriceSnapshotRepository;

@Service
public class ReservationPriceSnapshotService {

    private final ReservationPriceSnapshotRepository reservationPriceSnapshotRepository;

    public ReservationPriceSnapshotService(
        ReservationPriceSnapshotRepository reservationPriceSnapshotRepository
    ) {
        this.reservationPriceSnapshotRepository = reservationPriceSnapshotRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ReservationPriceSnapshot> findByCapacityHoldId(Long holdId) {
        return reservationPriceSnapshotRepository.findByCapacityHoldHoldId(holdId);
    }
}
