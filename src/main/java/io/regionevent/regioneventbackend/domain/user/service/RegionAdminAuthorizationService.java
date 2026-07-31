package io.regionevent.regioneventbackend.domain.user.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
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

    public void authorize(
        Long userId,
        Long targetRegionId
    ) {
        Long authorizedRegionId = requireAuthorizedRegionId(userId);
        if (!authorizedRegionId.equals(targetRegionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public Long requireAuthorizedRegionId(Long userId) {
        UserRoleAssignment assignment = userRoleAssignmentRepository
            .findByIdUserIdAndIdRoleAndAppUserStatus(
                userId,
                UserRole.REGION_ADMIN,
                AppUserStatus.ACTIVE
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        Region assignedRegion = assignment.getRegion();
        if (assignedRegion == null || assignedRegion.getRegionId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return assignedRegion.getRegionId();
    }
}
