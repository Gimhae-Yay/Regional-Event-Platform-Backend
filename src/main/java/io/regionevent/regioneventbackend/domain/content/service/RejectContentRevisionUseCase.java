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
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

@Service
public class RejectContentRevisionUseCase {

    private final ContentRevisionService contentRevisionService;
    private final ContentService contentService;
    private final RegionService regionService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public RejectContentRevisionUseCase(
        ContentRevisionService contentRevisionService,
        ContentService contentService,
        RegionService regionService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.contentRevisionService = contentRevisionService;
        this.contentService = contentService;
        this.regionService = regionService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public RejectContentRevisionResult reject(
        Long userId,
        Long revisionId,
        String reason,
        UUID requestId
    ) {
        RegionAdminAuthorizationService.AuthorizedRegionAdmin regionAdmin =
            regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(userId);
        Long contentId = contentRevisionService.findContentIdByRevisionId(revisionId);
        Long regionId = contentService.findContentRegionId(contentId);
        Region region = regionService.findRegionForUpdate(regionId);
        Content content = contentService.findApprovalTargetForUpdate(contentId);
        ContentRevision revision = contentRevisionService.findReviewTargetForUpdate(revisionId);
        UserRoleAssignment reviewerAssignment = regionAdmin.authorize(
            region.getRegionId()
        );
        Instant reviewedAt = clock.instant();

        ContentRevision rejectedRevision = contentRevisionService.reject(
            revision,
            reviewerAssignment.getAppUser(),
            reviewedAt,
            reason
        );
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            AuditEventTargetType.CONTENT,
            content.getContentId(),
            ContentRevisionStatus.EDIT_REQUESTED.name(),
            ContentRevisionStatus.EDIT_REJECTED.name(),
            AuditEventResult.SUCCESS,
            null,
            new AuditEventActor(reviewerAssignment),
            reviewedAt
        ));
        return RejectContentRevisionResult.from(rejectedRevision);
    }
}
