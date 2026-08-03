package io.regionevent.regioneventbackend.domain.reservation.repository;

public interface ReservationCancellationLockTargetProjection {

    Long getUserId();

    Long getSessionId();
}
