package io.regionevent.regioneventbackend.domain.reservation.service;

public record ReservationReadResult(
    ReservationReadSnapshot snapshot,
    ReservationReadIntegrityValidator.CheckInInfo checkIn
) {
}
