package io.regionevent.regioneventbackend.domain.user.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminUserListProjection;

public record PlatformAdminUserListInfo(
    Long userId,
    String loginIdentifier,
    String name,
    List<RoleAssignmentInfo> roleAssignments,
    Instant createdAt
) {

    public PlatformAdminUserListInfo {
        roleAssignments = List.copyOf(roleAssignments);
    }

    public static List<PlatformAdminUserListInfo> from(
        List<PlatformAdminUserListProjection> projections
    ) {
        Map<Long, PlatformAdminUserListProjection> usersById = new LinkedHashMap<>();
        Map<Long, List<RoleAssignmentInfo>> assignmentsByUserId = new LinkedHashMap<>();

        for (PlatformAdminUserListProjection projection : projections) {
            usersById.putIfAbsent(projection.userId(), projection);
            assignmentsByUserId.putIfAbsent(projection.userId(), new ArrayList<>());
            if (projection.role() != null) {
                assignmentsByUserId.get(projection.userId()).add(new RoleAssignmentInfo(
                    projection.role(),
                    projection.regionId(),
                    projection.regionName()
                ));
            }
        }

        return usersById.values().stream()
            .map(user -> new PlatformAdminUserListInfo(
                user.userId(),
                user.loginIdentifier(),
                user.name(),
                assignmentsByUserId.get(user.userId()),
                user.createdAt()
            ))
            .toList();
    }

    public record RoleAssignmentInfo(
        UserRole role,
        Long regionId,
        String regionName
    ) {
    }
}
