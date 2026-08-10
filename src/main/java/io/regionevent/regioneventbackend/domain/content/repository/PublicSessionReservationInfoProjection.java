package io.regionevent.regioneventbackend.domain.content.repository;

import java.time.Instant;

public interface PublicSessionReservationInfoProjection {

    Long getSessionId();

    Long getContentId();

    int getReservationPrice();

    Instant getStartsAt();

    Instant getEndsAt();

    int getRemainingCapacity();

    boolean isStartsBeforeNow();
}
