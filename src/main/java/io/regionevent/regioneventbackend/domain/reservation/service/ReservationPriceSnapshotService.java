package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ReservationPriceSnapshot> findByHoldIdForUpdate(Long holdId) {
        return reservationPriceSnapshotRepository.findByHoldIdForUpdate(holdId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ReservationPriceSnapshot create(ReservationPriceSnapshot snapshot) {
        return reservationPriceSnapshotRepository.saveAndFlush(snapshot);
    }
}
