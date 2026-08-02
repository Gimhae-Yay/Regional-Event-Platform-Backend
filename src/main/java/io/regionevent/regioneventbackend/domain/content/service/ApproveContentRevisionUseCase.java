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
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

@Service
public class ApproveContentRevisionUseCase {

    private final ContentRevisionService contentRevisionService;
    private final OriginalContentReviewTargetService originalContentReviewTargetService;
    private final ContentLogService contentLogService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public ApproveContentRevisionUseCase(
        ContentRevisionService contentRevisionService,
        OriginalContentReviewTargetService originalContentReviewTargetService,
        ContentLogService contentLogService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.contentRevisionService = contentRevisionService;
        this.originalContentReviewTargetService = originalContentReviewTargetService;
        this.contentLogService = contentLogService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public ApproveContentRevisionResult approve(
        Long userId,
        Long revisionId,
        UUID requestId
    ) {
        ContentRevision revision = contentRevisionService.findReviewTargetForUpdate(revisionId);
        Content content = revision.getContent();
        UserRoleAssignment reviewerAssignment = regionAdminAuthorizationService.authorize(
            userId,
            content.getRegion().getRegionId()
        );
        boolean isPrePublicationRevisionByHistory = isPrePublicationRevisionByHistory(content);
        ContentStatus previousContentStatus = content.getStatus();
        Instant reviewedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);

        ContentRevision approvedRevision = contentRevisionService.approve(
            revision,
            reviewerAssignment.getAppUser(),
            reviewedAt,
            isPrePublicationRevisionByHistory
        );
        if (previousContentStatus == ContentStatus.PENDING) {
            contentLogService.recordApproved(
                content,
                reviewerAssignment.getAppUser(),
                reviewedAt
            );
        }
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            content.getRegion(),
            AuditEventTargetType.CONTENT,
            content.getContentId(),
            ContentRevisionStatus.EDIT_REQUESTED.name(),
            ContentRevisionStatus.EDIT_APPROVED.name(),
            AuditEventResult.SUCCESS,
            null,
            new AuditEventActor(reviewerAssignment),
            reviewedAt
        ));
        return ApproveContentRevisionResult.from(approvedRevision);
    }

    private boolean isPrePublicationRevisionByHistory(Content content) {
        if (content.getStatus() != ContentStatus.PENDING) {
            return false;
        }
        return originalContentReviewTargetService.findByContentId(content.getContentId())
            .map(target -> target.type() == OriginalContentReviewTargetType.PRE_PUBLICATION_REVISION)
            .orElse(false);
    }
}
