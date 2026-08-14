package io.regionevent.regioneventbackend.domain.content.service;

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
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class RejectSessionRevisionUseCase {

    private final SessionRevisionService sessionRevisionService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public RejectSessionRevisionUseCase(
        SessionRevisionService sessionRevisionService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.sessionRevisionService = sessionRevisionService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public RejectSessionRevisionResult reject(
        Long userId,
        Long revisionId,
        String reason,
        UUID requestId
    ) {
        String normalizedReason = normalizeReason(reason);
        RegionAdminAuthorizationService.AuthorizedRegionAdmin regionAdmin =
            regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(userId);
        SessionRevision revision = sessionRevisionService.findReviewTargetForUpdate(revisionId);
        UserRoleAssignment reviewerAssignment = regionAdmin.authorize(
            revision.getRegion().getRegionId()
        );
        AuditEventActor reviewer = new AuditEventActor(reviewerAssignment);
        Instant reviewedAt = clock.instant();

        sessionRevisionService.rejectPending(
            revision.getSessionRevisionId(),
            reviewerAssignment.getAppUser(),
            reviewedAt,
            normalizedReason
        );
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            revision.getRegion(),
            AuditEventTargetType.CONTENT_SESSION,
            revision.getTargetSession().getSessionId(),
            SessionRevisionStatus.PENDING.name(),
            SessionRevisionStatus.REJECTED.name(),
            AuditEventResult.SUCCESS,
            null,
            reviewer,
            reviewedAt
        ));
        return new RejectSessionRevisionResult(
            revision.getSessionRevisionId(),
            SessionRevisionStatus.REJECTED,
            revision.getContent().getContentId(),
            revision.getTargetSession().getSessionId(),
            normalizedReason,
            reviewedAt
        );
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String normalizedReason = reason.strip();
        if (normalizedReason.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return normalizedReason;
    }
}
