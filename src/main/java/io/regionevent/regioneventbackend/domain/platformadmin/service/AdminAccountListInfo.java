package io.regionevent.regioneventbackend.domain.platformadmin.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAccountListProjection;

public record AdminAccountListInfo(
    Long userId,
    String loginIdentifier,
    String name,
    PlatformAdminGrade grade,
    PlatformAdminAssignmentStatus status,
    Instant createdAt,
    Instant inactivatedAt
) {

    public static List<AdminAccountListInfo> from(List<PlatformAdminAccountListProjection> projections) {
        return projections.stream()
            .map(projection -> new AdminAccountListInfo(
                projection.userId(),
                projection.loginIdentifier(),
                projection.name(),
                projection.grade(),
                projection.status(),
                projection.grantedAt(),
                projection.inactivatedAt()
            ))
            .toList();
    }
}
