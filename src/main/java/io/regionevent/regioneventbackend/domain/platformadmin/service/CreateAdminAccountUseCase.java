package io.regionevent.regioneventbackend.domain.platformadmin.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
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
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAssignmentService;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreateAdminAccountUseCase {

    private static final int MAX_PASSWORD_BYTES = 72;

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final AppUserService appUserService;
    private final PlatformAdminAssignmentService platformAdminAssignmentService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public CreateAdminAccountUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        AppUserService appUserService,
        PlatformAdminAssignmentService platformAdminAssignmentService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.appUserService = appUserService;
        this.platformAdminAssignmentService = platformAdminAssignmentService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public CreateAdminAccountResult create(
        Long actorUserId,
        CreateAdminAccountCommand command,
        UUID requestId
    ) {
        validateCommand(command);
        PlatformAdminAssignment actor = platformAdminAuthorizationService.requireAuthorizedSuperAdmin(actorUserId);
        PlatformAdminGrade grade = PlatformAdminGrade.valueOf(command.grade());
        AppUser user = appUserService.createActivePrivilegedUser(
            command.email(),
            command.password(),
            command.name(),
            command.phone()
        );
        PlatformAdminAssignment assignment = platformAdminAssignmentService.createActiveAssignment(user, grade);
        Instant createdAt = clock.instant();
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            null,
            AuditEventTargetType.PLATFORM_ADMIN_ASSIGNMENT,
            assignment.getPlatformAdminAssignmentId(),
            null,
            assignment.getStatus().name(),
            AuditEventResult.SUCCESS,
            command.reasonCode(),
            null,
            command.evidenceReference(),
            new AuditEventActor(actor),
            createdAt
        ));
        return new CreateAdminAccountResult(
            user.getUserId(),
            assignment.getPlatformAdminAssignmentId(),
            assignment.getGrade().name(),
            assignment.getStatus().name(),
            createdAt
        );
    }

    private void validateCommand(CreateAdminAccountCommand command) {
        if (command == null
            || command.email() == null
            || command.password() == null
            || command.name() == null
            || command.phone() == null
            || command.grade() == null
            || command.reasonCode() == null
            || command.evidenceReference() == null
            || command.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    public record CreateAdminAccountCommand(
        String email,
        String password,
        String name,
        String phone,
        String grade,
        String reasonCode,
        String evidenceReference
    ) {
    }
}
