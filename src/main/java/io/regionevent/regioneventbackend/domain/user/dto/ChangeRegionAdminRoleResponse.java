package io.regionevent.regioneventbackend.domain.user.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.user.service.RegionAdminRoleChangeResult;

public record ChangeRegionAdminRoleResponse(
    String userId,
    String roleAssignmentId,
    String role,
    String regionId,
    String status,
    Instant grantedAt,
    Instant revokedAt
) {

    public static ChangeRegionAdminRoleResponse from(RegionAdminRoleChangeResult result) {
        return new ChangeRegionAdminRoleResponse(
            result.userId().toString(),
            result.roleAssignmentId().toString(),
            result.role() == null ? null : result.role().name(),
            result.regionId().toString(),
            result.status().name(),
            result.grantedAt(),
            result.revokedAt()
        );
    }
}
