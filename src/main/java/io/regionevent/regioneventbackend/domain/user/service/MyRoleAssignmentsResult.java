package io.regionevent.regioneventbackend.domain.user.service;

import java.util.List;

import io.regionevent.regioneventbackend.domain.user.entity.UserRole;

public record MyRoleAssignmentsResult(
    List<RoleAssignment> roleAssignments
) {

    public MyRoleAssignmentsResult {
        roleAssignments = List.copyOf(roleAssignments);
    }

    public record RoleAssignment(
        UserRole role,
        Long regionId,
        String regionName
    ) {
    }
}
