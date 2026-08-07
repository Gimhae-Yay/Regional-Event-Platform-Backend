package io.regionevent.regioneventbackend.domain.reservation.service;

import io.regionevent.regioneventbackend.domain.reservation.service.ReservationParticipantMasker.MaskedParticipant;

public record RegionAdminReservationSearchResult(
    ReservationReadSnapshot.ReservationInfo reservation,
    ReservationReadSnapshot.SessionInfo session,
    ReservationReadSnapshot.ContentInfo content,
    MaskedParticipant participant,
    ReservationReadIntegrityValidator.CheckInInfo checkIn
) {
}
