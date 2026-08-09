package io.regionevent.regioneventbackend.domain.region.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.region.service.CreateRegionResult;

public record CreateRegionResponse(
    String regionId,
    String regionCode,
    String name,
    boolean isPublic,
    Instant createdAt,
    Instant updatedAt
) {

    public static CreateRegionResponse from(CreateRegionResult result) {
        return new CreateRegionResponse(
            result.regionId().toString(),
            result.regionCode(),
            result.name(),
            result.isPublic(),
            result.createdAt(),
            result.updatedAt()
        );
    }
}
