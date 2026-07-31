package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationNumberGenerator reservationNumberGenerator;

    public ReservationService(
        ReservationRepository reservationRepository,
        ReservationNumberGenerator reservationNumberGenerator
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationNumberGenerator = reservationNumberGenerator;
    }

    public Reservation createConfirmed(CapacityHold capacityHold, AppUser user, Instant confirmedAt) {
        return reservationRepository.saveAndFlush(new Reservation(
            reservationNumberGenerator.generate(),
            UUID.randomUUID().toString(),
            capacityHold.getRegion(),
            capacityHold,
            capacityHold.getContentSession(),
            user,
            ReservationStatus.CONFIRMED,
            confirmedAt,
            null,
            null,
            null,
            null
        ));
    }
}
