package io.regionevent.regioneventbackend.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class RegionAdminAuthorizationService {

    private final AppUserRepository appUserRepository;

    public RegionAdminAuthorizationService(
        AppUserRepository appUserRepository
    ) {
        this.appUserRepository = appUserRepository;
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

    @Transactional
    public UserRoleAssignment authorizeForUpdate(
        Long userId,
        Long targetRegionId
    ) {
        AuthorizedRegionAdmin regionAdmin = requireAuthorizedRegionAdminForUpdate(userId);
        if (!regionAdmin.region().getRegionId().equals(targetRegionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return regionAdmin.roleAssignment();
    }

    @Transactional
    public Long requireAuthorizedRegionIdForUpdate(Long userId) {
        return requireAuthorizedRegionAdminForUpdate(userId).region().getRegionId();
    }

    public AuthorizedRegionAdmin requireAuthorizedRegionAdmin(Long userId) {
        UserRoleAssignment assignment = requireAuthorizedAssignment(userId);
        return new AuthorizedRegionAdmin(
            assignment.getAppUser(),
            requireAssignedRegion(assignment),
            assignment
        );
    }

    @Transactional
    public AuthorizedRegionAdmin requireAuthorizedRegionAdminForUpdate(Long userId) {
        UserRoleAssignment assignment = requireAuthorizedAssignmentForUpdate(userId);
        return new AuthorizedRegionAdmin(
            assignment.getAppUser(),
            requireAssignedRegion(assignment),
            assignment
        );
    }

    private UserRoleAssignment requireAuthorizedAssignment(Long userId) {
        validateUserId(userId);
        return appUserRepository.findActiveRoleAssignment(
                userId,
                UserRole.REGION_ADMIN,
                UserRoleAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private UserRoleAssignment requireAuthorizedAssignmentForUpdate(Long userId) {
        validateUserId(userId);
        lockActiveUser(userId);
        return appUserRepository.findActiveRoleAssignmentForUpdate(
                userId,
                UserRole.REGION_ADMIN,
                UserRoleAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private void lockActiveUser(Long userId) {
        appUserRepository.findByIdForUpdate(userId)
            .filter(user -> user.getStatus() == AppUserStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
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
