package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.repository.PublicSessionReservationInfoProjection;

public record PublicSessionReservationInfo(
    Long sessionId,
    Long contentId,
    int reservationPrice,
    Instant startsAt,
    Instant endsAt,
    int remainingCapacity,
    boolean startsBeforeNow
) {

    public PublicSessionReservationInfo(
        Long sessionId,
        Long contentId,
        Instant startsAt,
        Instant endsAt,
        int remainingCapacity,
        boolean startsBeforeNow
    ) {
        this(sessionId, contentId, 0, startsAt, endsAt, remainingCapacity, startsBeforeNow);
    }

    public static PublicSessionReservationInfo from(PublicSessionReservationInfoProjection projection) {
        return new PublicSessionReservationInfo(
            projection.getSessionId(),
            projection.getContentId(),
            projection.getReservationPrice(),
            projection.getStartsAt(),
            projection.getEndsAt(),
            projection.getRemainingCapacity(),
            projection.isStartsBeforeNow()
        );
    }

    public boolean isReservable() {
        return startsBeforeNow && remainingCapacity >= 1;
    }
}
