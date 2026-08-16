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
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ApproveContentUseCase {

    private final ContentService contentService;
    private final OriginalContentReviewTargetService originalContentReviewTargetService;
    private final ContentSessionService contentSessionService;
    private final ContentLogService contentLogService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public ApproveContentUseCase(
        ContentService contentService,
        OriginalContentReviewTargetService originalContentReviewTargetService,
        ContentSessionService contentSessionService,
        ContentLogService contentLogService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.contentService = contentService;
        this.originalContentReviewTargetService = originalContentReviewTargetService;
        this.contentSessionService = contentSessionService;
        this.contentLogService = contentLogService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public ApproveContentResult approve(
        Long userId,
        Long contentId,
        UUID requestId
    ) {
        RegionAdminAuthorizationService.AuthorizedRegionAdmin regionAdmin =
            regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(userId);
        Content content = contentService.findApprovalTargetForUpdate(contentId);
        UserRoleAssignment reviewerAssignment = regionAdmin.authorize(
            content.getRegion().getRegionId()
        );

        if (content.getStatus() == ContentStatus.APPROVED) {
            ContentLog approvedLog = contentLogService.findLatestApproved(contentId);
            return ApproveContentResult.from(content, approvedLog.getDate());
        }
        if (content.getStatus() != ContentStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }

        OriginalContentReviewTarget reviewTarget = originalContentReviewTargetService
            .findByContentId(contentId)
            .filter(OriginalContentReviewTarget::isOriginalReviewTarget)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT));
        if (!reviewTarget.content().getContentId().equals(content.getContentId())) {
            throw new IllegalStateException("review target content must match locked content");
        }

        Instant approvedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<ContentSession> sessions = contentSessionService.findApprovalTargetsForUpdate(contentId);
        contentSessionService.approveAll(
            sessions,
            reviewerAssignment.getAppUser(),
            approvedAt
        );
        Content approvedContent = contentService.approve(content);
        contentLogService.recordApproved(
            approvedContent,
            reviewerAssignment.getAppUser(),
            approvedAt
        );
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            approvedContent.getRegion(),
            AuditEventTargetType.CONTENT,
            approvedContent.getContentId(),
            ContentStatus.PENDING.name(),
            ContentStatus.APPROVED.name(),
            AuditEventResult.SUCCESS,
            null,
            new AuditEventActor(reviewerAssignment),
            approvedAt
        ));
        return ApproveContentResult.from(approvedContent, approvedAt);
    }
}
