package io.regionevent.regioneventbackend.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class PlatformAdminAuthorizationService {

    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;

    public PlatformAdminAuthorizationService(
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository
    ) {
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
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

    private PlatformAdminAssignment requireActivePrivilegedAssignment(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return platformAdminAssignmentRepository
            .findByAppUserUserIdAndStatusAndAppUserStatusAndAppUserAccountKind(
                userId,
                PlatformAdminAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE,
                AppUserAccountKind.PRIVILEGED
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }
}
