package io.regionevent.regioneventbackend.domain.reservation.repository;

import io.regionevent.regioneventbackend.domain.region.entity.Region;

public interface ManualCheckInLookupProjection {

    Long getReservationId();

    Region getReservationRegion();

    Long getContentRegionId();

    Long getOperatorId();
}
