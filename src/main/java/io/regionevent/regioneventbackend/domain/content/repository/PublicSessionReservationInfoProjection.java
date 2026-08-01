package io.regionevent.regioneventbackend.domain.content.repository;

import java.time.Instant;

public interface PublicSessionReservationInfoProjection {

    Long getSessionId();

    Long getContentId();

    Instant getStartsAt();

    Instant getEndsAt();

    int getRemainingCapacity();

    boolean isStartsBeforeNow();
}
