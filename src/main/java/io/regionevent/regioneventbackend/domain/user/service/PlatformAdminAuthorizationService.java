package io.regionevent.regioneventbackend.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
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
        return requirePrivilegedAssignment(userId);
    }

    @Transactional(readOnly = true)
    public PlatformAdminAssignment requireAuthorizedSuperAdmin(Long userId) {
        return requirePrivilegedAssignment(userId);
    }

    @Transactional
    public PlatformAdminAssignment requireAuthorizedPlatformAdminForUpdate(Long userId) {
        return requirePrivilegedAssignmentForUpdate(userId);
    }

    @Transactional
    public PlatformAdminAssignment requireAuthorizedSuperAdminForUpdate(Long userId) {
        return requirePrivilegedAssignmentForUpdate(userId);
    }

    private PlatformAdminAssignment requirePrivilegedAssignment(Long userId) {
        validateUserId(userId);
        return appUserRepository.findPrivilegedAssignment(
                userId,
                AppUserStatus.ACTIVE,
                AppUserAccountKind.PRIVILEGED
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private PlatformAdminAssignment requirePrivilegedAssignmentForUpdate(Long userId) {
        validateUserId(userId);
        lockActivePrivilegedUser(userId);
        return appUserRepository.findPrivilegedAssignmentForUpdate(
                userId,
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
