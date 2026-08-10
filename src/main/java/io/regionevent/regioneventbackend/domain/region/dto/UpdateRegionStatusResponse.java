package io.regionevent.regioneventbackend.domain.region.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.region.service.UpdateRegionStatusResult;

public record UpdateRegionStatusResponse(
    String regionId,
    String regionCode,
    String name,
    boolean isPublic,
    Instant updatedAt
) {

    public static UpdateRegionStatusResponse from(UpdateRegionStatusResult result) {
        return new UpdateRegionStatusResponse(
            result.regionId().toString(),
            result.regionCode(),
            result.name(),
            result.isPublic(),
            result.updatedAt()
        );
    }
}
