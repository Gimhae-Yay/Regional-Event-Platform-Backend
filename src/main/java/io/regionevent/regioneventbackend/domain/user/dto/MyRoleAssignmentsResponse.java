package io.regionevent.regioneventbackend.domain.user.dto;

import java.util.List;

import io.regionevent.regioneventbackend.domain.user.service.MyRoleAssignmentsResult;

public record MyRoleAssignmentsResponse(
    List<RoleAssignment> roleAssignments
) {

    public MyRoleAssignmentsResponse {
        roleAssignments = List.copyOf(roleAssignments);
    }

    public static MyRoleAssignmentsResponse from(MyRoleAssignmentsResult result) {
        return new MyRoleAssignmentsResponse(
            result.roleAssignments().stream()
                .map(RoleAssignment::from)
                .toList()
        );
    }

    public record RoleAssignment(
        String role,
        String regionId,
        String regionName
    ) {

        private static RoleAssignment from(MyRoleAssignmentsResult.RoleAssignment assignment) {
            String regionId = assignment.regionId() == null ? null : assignment.regionId().toString();
            return new RoleAssignment(assignment.role().name(), regionId, assignment.regionName());
        }
    }
}
