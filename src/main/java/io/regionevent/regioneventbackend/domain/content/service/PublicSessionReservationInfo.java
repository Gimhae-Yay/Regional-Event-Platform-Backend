package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.repository.PublicSessionReservationInfoProjection;

public record PublicSessionReservationInfo(
    Long sessionId,
    Long contentId,
    Instant startsAt,
    Instant endsAt,
    int remainingCapacity,
    boolean startsBeforeNow
) {

    public static PublicSessionReservationInfo from(PublicSessionReservationInfoProjection projection) {
        return new PublicSessionReservationInfo(
            projection.getSessionId(),
            projection.getContentId(),
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
