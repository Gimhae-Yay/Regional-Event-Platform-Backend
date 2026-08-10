package io.regionevent.regioneventbackend.domain.region.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.region.entity.Region;

public record CreateRegionResult(
    Long regionId,
    String regionCode,
    String name,
    boolean isPublic,
    Instant createdAt,
    Instant updatedAt
) {

    public static CreateRegionResult from(Region region) {
        return new CreateRegionResult(
            region.getRegionId(),
            region.getRegionCode(),
            region.getName(),
            region.isPublic(),
            region.getCreatedAt(),
            region.getUpdatedAt()
        );
    }
}
