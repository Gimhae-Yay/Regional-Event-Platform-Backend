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
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class RejectContentUseCase {

    private final ContentService contentService;
    private final OriginalContentReviewTargetService originalContentReviewTargetService;
    private final ContentLogService contentLogService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public RejectContentUseCase(
        ContentService contentService,
        OriginalContentReviewTargetService originalContentReviewTargetService,
        ContentLogService contentLogService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.contentService = contentService;
        this.originalContentReviewTargetService = originalContentReviewTargetService;
        this.contentLogService = contentLogService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public RejectContentResult reject(
        Long userId,
        Long contentId,
        String reason,
        UUID requestId
    ) {
        String normalizedReason = normalizeReason(reason);
        RegionAdminAuthorizationService.AuthorizedRegionAdmin regionAdmin =
            regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(userId);
        Content content = contentService.findApprovalTargetForUpdate(contentId);
        UserRoleAssignment reviewerAssignment = regionAdmin.authorize(
            content.getRegion().getRegionId()
        );

        if (content.getStatus() == ContentStatus.REJECTED) {
            return resolveIdempotentResult(content, normalizedReason);
        }
        if (content.getStatus() != ContentStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        AuditEventActor reviewer = new AuditEventActor(reviewerAssignment);

        OriginalContentReviewTarget reviewTarget = originalContentReviewTargetService
            .findByContentId(contentId)
            .filter(OriginalContentReviewTarget::isOriginalReviewTarget)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT));
        if (!reviewTarget.content().getContentId().equals(content.getContentId())) {
            throw new IllegalStateException("review target content must match locked content");
        }

        Instant rejectedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Content rejectedContent = contentService.reject(content, rejectedAt);
        contentLogService.recordRejected(
            rejectedContent,
            reviewer.getAppUser(),
            rejectedAt,
            normalizedReason
        );
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            rejectedContent.getRegion(),
            AuditEventTargetType.CONTENT,
            rejectedContent.getContentId(),
            ContentStatus.PENDING.name(),
            ContentStatus.REJECTED.name(),
            AuditEventResult.SUCCESS,
            null,
            reviewer,
            rejectedAt
        ));
        return RejectContentResult.from(rejectedContent, rejectedAt);
    }

    private RejectContentResult resolveIdempotentResult(Content content, String reason) {
        ContentLog rejectedLog = contentLogService.findLatestRejected(content.getContentId());
        if (!reason.equals(rejectedLog.getReason())) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        return RejectContentResult.from(content, rejectedLog.getDate());
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return reason.strip();
    }
}
