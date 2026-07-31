package io.regionevent.regioneventbackend.domain.user.service;

import java.util.Objects;

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
        UserRoleAssignment assignment = userRoleAssignmentRepository
            .findByIdUserIdAndIdRoleAndAppUserStatus(
                userId,
                UserRole.REGION_ADMIN,
                AppUserStatus.ACTIVE
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        validateAssignedRegion(assignment.getRegion(), targetRegionId);
    }

    private void validateAssignedRegion(
        Region assignedRegion,
        Long targetRegionId
    ) {
        if (assignedRegion == null || !Objects.equals(assignedRegion.getRegionId(), targetRegionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
