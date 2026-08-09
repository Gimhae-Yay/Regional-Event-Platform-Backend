package io.regionevent.regioneventbackend.domain.user.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class RegionAdminAuthorizationService {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public RegionAdminAuthorizationService(
        UserRoleAssignmentRepository userRoleAssignmentRepository
    ) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    public UserRoleAssignment authorize(
        Long userId,
        Long targetRegionId
    ) {
        UserRoleAssignment assignment = requireAuthorizedAssignment(userId);
        Region assignedRegion = requireAssignedRegion(assignment);
        if (!assignedRegion.getRegionId().equals(targetRegionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return assignment;
    }

    public Long requireAuthorizedRegionId(Long userId) {
        return requireAssignedRegion(requireAuthorizedAssignment(userId)).getRegionId();
    }

    private UserRoleAssignment requireAuthorizedAssignment(Long userId) {
        return userRoleAssignmentRepository
            .findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
                userId,
                UserRole.REGION_ADMIN,
                UserRoleAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private Region requireAssignedRegion(UserRoleAssignment assignment) {
        Region assignedRegion = assignment.getRegion();
        if (assignedRegion == null || assignedRegion.getRegionId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return assignedRegion;
    }
}
