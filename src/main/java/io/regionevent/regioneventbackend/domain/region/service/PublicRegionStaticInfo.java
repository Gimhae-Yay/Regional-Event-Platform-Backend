package io.regionevent.regioneventbackend.domain.region.service;

import io.regionevent.regioneventbackend.domain.region.repository.PublicRegionStaticProjection;

public record PublicRegionStaticInfo(
    Long regionId,
    String regionCode,
    String name
) {

    public PublicRegionStaticInfo {
        if (regionId == null || regionId <= 0) {
            throw new IllegalArgumentException("regionId must be positive");
        }
        if (regionCode == null || regionCode.isBlank()) {
            throw new IllegalArgumentException("regionCode must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    public static PublicRegionStaticInfo from(PublicRegionStaticProjection projection) {
        return new PublicRegionStaticInfo(
            projection.regionId(),
            projection.regionCode(),
            projection.name()
        );
    }
}
