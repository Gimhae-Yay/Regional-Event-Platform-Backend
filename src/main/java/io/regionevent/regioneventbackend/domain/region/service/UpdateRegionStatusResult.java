package io.regionevent.regioneventbackend.domain.region.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.region.entity.Region;

public record UpdateRegionStatusResult(
    Long regionId,
    String regionCode,
    String name,
    boolean isPublic,
    Instant updatedAt
) {

    public static UpdateRegionStatusResult from(Region region) {
        return new UpdateRegionStatusResult(
            region.getRegionId(),
            region.getRegionCode(),
            region.getName(),
            region.isPublic(),
            region.getUpdatedAt()
        );
    }
}
