package io.regionevent.regioneventbackend.domain.region.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.region.repository.PlatformAdminRegionListProjection;

public record PlatformAdminRegionListInfo(
    Long regionId,
    String regionCode,
    String name,
    boolean isPublic,
    long regionAdminCount,
    Instant createdAt,
    Instant updatedAt
) {

    public static PlatformAdminRegionListInfo from(PlatformAdminRegionListProjection projection) {
        return new PlatformAdminRegionListInfo(
            projection.regionId(),
            projection.regionCode(),
            projection.name(),
            projection.isPublic(),
            projection.regionAdminCount(),
            projection.createdAt(),
            projection.updatedAt()
        );
    }
}
