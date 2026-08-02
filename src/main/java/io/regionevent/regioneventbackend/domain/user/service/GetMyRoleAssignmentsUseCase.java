package io.regionevent.regioneventbackend.domain.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;

@Service
public class GetMyRoleAssignmentsUseCase {

    private final AppUserService appUserService;
    private final UserRoleAssignmentService userRoleAssignmentService;

    public GetMyRoleAssignmentsUseCase(
        AppUserService appUserService,
        UserRoleAssignmentService userRoleAssignmentService
    ) {
        this.appUserService = appUserService;
        this.userRoleAssignmentService = userRoleAssignmentService;
    }

    @Transactional(readOnly = true)
    public MyRoleAssignmentsResult get(Long userId) {
        appUserService.findActiveUser(userId);

        List<MyRoleAssignmentsResult.RoleAssignment> roleAssignments = userRoleAssignmentService
            .findRoleAssignmentsByUserId(userId)
            .stream()
            .map(this::toRoleAssignment)
            .toList();

        return new MyRoleAssignmentsResult(roleAssignments);
    }

    private MyRoleAssignmentsResult.RoleAssignment toRoleAssignment(UserRoleAssignment assignment) {
        Region region = assignment.getRegion();
        if (region == null) {
            return new MyRoleAssignmentsResult.RoleAssignment(assignment.getRole(), null, null);
        }
        return new MyRoleAssignmentsResult.RoleAssignment(
            assignment.getRole(),
            region.getRegionId(),
            region.getName()
        );
    }
}
