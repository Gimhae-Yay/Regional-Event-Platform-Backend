package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class RejectContentSessionUseCase {

    private final ContentSessionService contentSessionService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public RejectContentSessionUseCase(
        ContentSessionService contentSessionService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.contentSessionService = contentSessionService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public RejectContentSessionResult reject(
        Long userId,
        Long sessionId,
        String reason,
        UUID requestId
    ) {
        String normalizedReason = normalizeReason(reason);
        ContentSession contentSession = contentSessionService.findRejectTargetForUpdate(sessionId);
        UserRoleAssignment reviewer = regionAdminAuthorizationService.authorize(
            userId,
            contentSession.getRegion().getRegionId()
        );
        Instant reviewedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ContentSession rejectedSession = contentSessionService.reject(
            contentSession,
            reviewer.getAppUser(),
            reviewedAt,
            normalizedReason
        );
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            rejectedSession.getRegion(),
            AuditEventTargetType.CONTENT_SESSION,
            rejectedSession.getSessionId(),
            ContentSessionStatus.PENDING.name(),
            ContentSessionStatus.REJECTED.name(),
            AuditEventResult.SUCCESS,
            null,
            new AuditEventActor(reviewer),
            reviewedAt
        ));
        return RejectContentSessionResult.from(rejectedSession);
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return reason.trim();
    }
}
