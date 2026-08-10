package io.regionevent.regioneventbackend.domain.user.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminUserListInfo;

public record GetPlatformAdminUsersResponse(List<UserResponse> users) {

    public GetPlatformAdminUsersResponse {
        users = List.copyOf(users);
    }

    public static GetPlatformAdminUsersResponse from(List<PlatformAdminUserListInfo> users) {
        return new GetPlatformAdminUsersResponse(users.stream()
            .map(UserResponse::from)
            .toList());
    }

    public record UserResponse(
        String userId,
        String loginIdentifier,
        String name,
        List<RoleAssignmentResponse> roleAssignments,
        Instant createdAt
    ) {

        private static UserResponse from(PlatformAdminUserListInfo user) {
            return new UserResponse(
                user.userId().toString(),
                user.loginIdentifier(),
                user.name(),
                user.roleAssignments().stream()
                    .map(RoleAssignmentResponse::from)
                    .toList(),
                user.createdAt()
            );
        }
    }

    public record RoleAssignmentResponse(
        String role,
        String regionId,
        String regionName
    ) {

        private static RoleAssignmentResponse from(PlatformAdminUserListInfo.RoleAssignmentInfo assignment) {
            return new RoleAssignmentResponse(
                assignment.role().name(),
                assignment.regionId() == null ? null : assignment.regionId().toString(),
                assignment.regionName()
            );
        }
    }
}
