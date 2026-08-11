package io.regionevent.regioneventbackend.domain.user.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
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
        AuthorizedRegionAdmin regionAdmin = requireAuthorizedRegionAdmin(userId);
        if (!regionAdmin.region().getRegionId().equals(targetRegionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return regionAdmin.roleAssignment();
    }

    public Long requireAuthorizedRegionId(Long userId) {
        return requireAuthorizedRegionAdmin(userId).region().getRegionId();
    }

    public AuthorizedRegionAdmin requireAuthorizedRegionAdmin(Long userId) {
        UserRoleAssignment assignment = requireAuthorizedAssignment(userId);
        return new AuthorizedRegionAdmin(
            assignment.getAppUser(),
            requireAssignedRegion(assignment),
            assignment
        );
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

    public record AuthorizedRegionAdmin(
        AppUser user,
        Region region,
        UserRoleAssignment roleAssignment
    ) {

        public AuthorizedRegionAdmin {
            if (user == null || user.getUserId() == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (region == null || region.getRegionId() == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (roleAssignment == null || roleAssignment.getRoleAssignmentId() == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
    }
}
