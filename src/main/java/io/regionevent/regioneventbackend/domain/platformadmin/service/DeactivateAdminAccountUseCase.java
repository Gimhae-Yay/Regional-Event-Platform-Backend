package io.regionevent.regioneventbackend.domain.platformadmin.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAssignmentService;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class DeactivateAdminAccountUseCase {

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final PlatformAdminAssignmentService platformAdminAssignmentService;
    private final AppUserService appUserService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public DeactivateAdminAccountUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        PlatformAdminAssignmentService platformAdminAssignmentService,
        AppUserService appUserService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.platformAdminAssignmentService = platformAdminAssignmentService;
        this.appUserService = appUserService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public DeactivateAdminAccountResult deactivate(
        Long actorUserId,
        Long targetUserId,
        DeactivateAdminAccountCommand command,
        UUID requestId
    ) {
        validateCommand(command);
        List<AppUser> lockedUsers = appUserService.findUsersForUpdate(actorUserId, targetUserId);
        PlatformAdminAssignment actor = platformAdminAuthorizationService
            .requireAuthorizedSuperAdminForUpdate(actorUserId);
        AppUser targetUser = lockedUsers.stream()
            .filter(user -> user.getUserId().equals(targetUserId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        PlatformAdminAssignment target = platformAdminAssignmentService.findAssignmentForUpdate(targetUser.getUserId())
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        List<PlatformAdminAssignment> activeSuperAdmins = platformAdminAssignmentService
            .findActiveSuperAdminsForUpdate();
        validateDeactivation(actor, target, activeSuperAdmins);

        Instant inactivatedAt = clock.instant();
        target.inactivate(inactivatedAt, command.reasonCode());
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            null,
            AuditEventTargetType.PLATFORM_ADMIN_ASSIGNMENT,
            target.getPlatformAdminAssignmentId(),
            PlatformAdminAssignmentStatus.ACTIVE.name(),
            PlatformAdminAssignmentStatus.INACTIVE.name(),
            AuditEventResult.SUCCESS,
            command.reasonCode(),
            command.evidenceReference(),
            new AuditEventActor(actor),
            inactivatedAt
        ));
        return new DeactivateAdminAccountResult(
            target.getAppUser().getUserId(),
            target.getPlatformAdminAssignmentId(),
            target.getGrade().name(),
            target.getStatus().name(),
            inactivatedAt
        );
    }

    private void validateCommand(DeactivateAdminAccountCommand command) {
        if (command == null
            || command.reasonCode() == null
            || command.reasonCode().isBlank()
            || command.evidenceReference() == null
            || command.evidenceReference().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateDeactivation(
        PlatformAdminAssignment actor,
        PlatformAdminAssignment target,
        List<PlatformAdminAssignment> activeSuperAdmins
    ) {
        if (actor.getAppUser().getUserId().equals(target.getAppUser().getUserId())
            || !target.isActive()
            || (target.getGrade() == PlatformAdminGrade.SUPER_ADMIN
                && activeSuperAdmins.size() <= 1)) {
            throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_DEACTIVATION_CONFLICT);
        }
    }

    public record DeactivateAdminAccountCommand(String reasonCode, String evidenceReference) {
    }
}
