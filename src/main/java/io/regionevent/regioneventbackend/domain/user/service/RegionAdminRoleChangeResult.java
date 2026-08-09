package io.regionevent.regioneventbackend.domain.user.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;

public record RegionAdminRoleChangeResult(
    Long userId,
    Long roleAssignmentId,
    UserRole role,
    Long regionId,
    UserRoleAssignmentStatus status,
    Instant grantedAt,
    Instant revokedAt
) {

    public static RegionAdminRoleChangeResult active(UserRoleAssignment assignment) {
        return from(assignment, assignment.getRole());
    }

    public static RegionAdminRoleChangeResult revoked(UserRoleAssignment assignment) {
        return from(assignment, null);
    }

    private static RegionAdminRoleChangeResult from(
        UserRoleAssignment assignment,
        UserRole role
    ) {
        return new RegionAdminRoleChangeResult(
            assignment.getAppUser().getUserId(),
            assignment.getRoleAssignmentId(),
            role,
            assignment.getRegion().getRegionId(),
            assignment.getStatus(),
            assignment.getGrantedAt(),
            assignment.getRevokedAt()
        );
    }
}
