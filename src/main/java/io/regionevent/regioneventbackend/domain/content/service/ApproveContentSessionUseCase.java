package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ApproveContentSessionUseCase {

    private static final List<ContentStatus> APPROVABLE_CONTENT_STATUSES = List.of(
        ContentStatus.APPROVED,
        ContentStatus.PUBLISHED
    );

    private final ContentService contentService;
    private final ContentSessionService contentSessionService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public ApproveContentSessionUseCase(
        ContentService contentService,
        ContentSessionService contentSessionService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public ApproveContentSessionResult approve(
        Long userId,
        Long sessionId,
        UUID requestId
    ) {
        Long contentId = contentSessionService.findContentIdBySessionId(sessionId);
        Content content = contentService.findApprovalTargetForUpdate(contentId);
        ContentSession contentSession = contentSessionService.findApprovalTargetForUpdate(sessionId);
        validateTarget(content, contentSession);
        UserRoleAssignment reviewerAssignment = regionAdminAuthorizationService.authorize(
            userId,
            content.getRegion().getRegionId()
        );
        Instant reviewedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ContentSession approvedSession = contentSessionService.approve(
            contentSession,
            reviewerAssignment.getAppUser(),
            reviewedAt
        );
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            content.getRegion(),
            AuditEventTargetType.CONTENT_SESSION,
            approvedSession.getSessionId(),
            ContentSessionStatus.PENDING.name(),
            ContentSessionStatus.SCHEDULED.name(),
            AuditEventResult.SUCCESS,
            null,
            new AuditEventActor(reviewerAssignment),
            reviewedAt
        ));
        return ApproveContentSessionResult.from(approvedSession);
    }

    private void validateTarget(
        Content content,
        ContentSession contentSession
    ) {
        if (!content.getContentId().equals(contentSession.getContent().getContentId())) {
            throw new IllegalStateException("locked content session must belong to locked content");
        }
        if (contentSession.getStatus() != ContentSessionStatus.PENDING
            || !APPROVABLE_CONTENT_STATUSES.contains(content.getStatus())) {
            throw new BusinessException(ErrorCode.SESSION_STATE_CONFLICT);
        }
    }
}
