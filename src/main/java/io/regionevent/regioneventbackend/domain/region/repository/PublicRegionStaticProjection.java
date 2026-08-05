package io.regionevent.regioneventbackend.domain.region.repository;

public record PublicRegionStaticProjection(
    Long regionId,
    String regionCode,
    String name
) {
}
