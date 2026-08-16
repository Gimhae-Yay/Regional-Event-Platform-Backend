package io.regionevent.regioneventbackend.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class PlatformAdminAuthorizationService {

    private final AppUserRepository appUserRepository;

    public PlatformAdminAuthorizationService(
        AppUserRepository appUserRepository
    ) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public PlatformAdminAssignment requireAuthorizedPlatformAdmin(Long userId) {
        return requireActivePrivilegedAssignment(userId);
    }

    @Transactional(readOnly = true)
    public PlatformAdminAssignment requireAuthorizedSuperAdmin(Long userId) {
        PlatformAdminAssignment assignment = requireActivePrivilegedAssignment(userId);
        if (assignment.getGrade() != PlatformAdminGrade.SUPER_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return assignment;
    }

    @Transactional
    public PlatformAdminAssignment requireAuthorizedPlatformAdminForUpdate(Long userId) {
        return requireActivePrivilegedAssignmentForUpdate(userId);
    }

    @Transactional
    public PlatformAdminAssignment requireAuthorizedSuperAdminForUpdate(Long userId) {
        PlatformAdminAssignment assignment = requireActivePrivilegedAssignmentForUpdate(userId);
        if (assignment.getGrade() != PlatformAdminGrade.SUPER_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return assignment;
    }

    private PlatformAdminAssignment requireActivePrivilegedAssignment(Long userId) {
        validateUserId(userId);
        return appUserRepository.findActivePrivilegedAssignment(
                userId,
                PlatformAdminAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE,
                AppUserAccountKind.PRIVILEGED
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private PlatformAdminAssignment requireActivePrivilegedAssignmentForUpdate(Long userId) {
        validateUserId(userId);
        lockActivePrivilegedUser(userId);
        return appUserRepository.findActivePrivilegedAssignmentForUpdate(
                userId,
                PlatformAdminAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE,
                AppUserAccountKind.PRIVILEGED
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private void lockActivePrivilegedUser(Long userId) {
        appUserRepository.findByIdForUpdate(userId)
            .filter(user -> user.getStatus() == AppUserStatus.ACTIVE)
            .filter(user -> user.getAccountKind() == AppUserAccountKind.PRIVILEGED)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
