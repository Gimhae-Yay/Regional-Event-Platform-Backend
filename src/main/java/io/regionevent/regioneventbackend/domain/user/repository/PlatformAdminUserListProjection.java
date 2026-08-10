package io.regionevent.regioneventbackend.domain.user.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.user.entity.UserRole;

public record PlatformAdminUserListProjection(
    Long userId,
    String loginIdentifier,
    String name,
    UserRole role,
    Long regionId,
    String regionName,
    Instant createdAt
) {
}
