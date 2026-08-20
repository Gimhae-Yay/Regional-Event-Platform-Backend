package io.regionevent.regioneventbackend.domain.user.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;

public record PlatformAdminAccountListProjection(
    Long userId,
    String loginIdentifier,
    String name,
    PlatformAdminGrade grade,
    PlatformAdminAssignmentStatus status,
    Instant grantedAt,
    Instant inactivatedAt
) {
}
