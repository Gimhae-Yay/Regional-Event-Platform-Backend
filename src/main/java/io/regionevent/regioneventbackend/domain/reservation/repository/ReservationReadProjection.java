package io.regionevent.regioneventbackend.domain.reservation.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;

public record ReservationReadProjection(
    Long reservationId,
    String reservationNo,
    ReservationStatus reservationStatus,
    Instant confirmedAt,
    Instant cancelledAt,
    String cancellationReason,
    Instant expiredAt,
    Long reservationRegionId,
    Long sessionId,
    ContentSessionStatus sessionStatus,
    Instant startsAt,
    Instant endsAt,
    Instant checkinOpenAt,
    Instant checkinCloseAt,
    Long sessionRegionId,
    Long contentId,
    String contentTitle,
    Long contentRegionId,
    Long participantUserId,
    String participantName,
    String participantPhone,
    Long visitId,
    Long visitReservationId,
    Long visitRegionId,
    Long visitSessionId,
    Long visitContentId,
    Long visitParticipantUserId,
    Instant checkedAt,
    Long holdId,
    CapacityHoldStatus holdStatus,
    Integer holdQuantity,
    Long holdSessionId,
    Long holdRegionId,
    Long holdReservationId
) {
}
