package io.regionevent.regioneventbackend.domain.region.repository;

import java.time.Instant;

public record PlatformAdminRegionListProjection(
    Long regionId,
    String regionCode,
    String name,
    boolean isPublic,
    long regionAdminCount,
    Instant createdAt,
    Instant updatedAt
) {
}
