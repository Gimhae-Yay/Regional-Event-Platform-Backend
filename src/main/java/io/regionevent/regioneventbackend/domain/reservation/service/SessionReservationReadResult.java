package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;

public record SessionReservationReadResult(
    Long reservationId,
    String reservationNo,
    ReservationStatus status,
    int quantity,
    Instant confirmedAt,
    ReservationParticipantMasker.MaskedParticipant participant,
    ReservationReadIntegrityValidator.CheckInInfo checkIn
) {
}
