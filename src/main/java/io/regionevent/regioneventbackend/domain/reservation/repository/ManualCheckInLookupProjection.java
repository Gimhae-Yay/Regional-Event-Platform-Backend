package io.regionevent.regioneventbackend.domain.reservation.repository;

public interface ManualCheckInLookupProjection {

    Long getReservationId();

    Long getReservationRegionId();

    Long getContentRegionId();

    Long getOperatorId();
}
