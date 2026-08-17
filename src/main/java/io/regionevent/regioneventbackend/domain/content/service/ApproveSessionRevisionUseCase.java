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
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ApproveSessionRevisionUseCase {

    private final SessionRevisionService sessionRevisionService;
    private final ContentService contentService;
    private final ContentSessionService contentSessionService;
    private final CapacityHoldService capacityHoldService;
    private final ReservationService reservationService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public ApproveSessionRevisionUseCase(
        SessionRevisionService sessionRevisionService,
        ContentService contentService,
        ContentSessionService contentSessionService,
        CapacityHoldService capacityHoldService,
        ReservationService reservationService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.sessionRevisionService = sessionRevisionService;
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
        this.capacityHoldService = capacityHoldService;
        this.reservationService = reservationService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public ApproveSessionRevisionResult approve(
        Long userId,
        Long revisionId,
        UUID requestId
    ) {
        RegionAdminAuthorizationService.AuthorizedRegionAdmin regionAdmin =
            regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(userId);
        Long contentId = sessionRevisionService.findContentIdByRevisionId(revisionId);
        Content content = contentService.findApprovalTargetForUpdate(contentId);
        SessionRevision revision = sessionRevisionService.findApprovalTargetForUpdate(revisionId);
        UserRoleAssignment reviewerAssignment = regionAdmin.authorize(
            content.getRegion().getRegionId()
        );
        ContentSession contentSession = contentSessionService.findRevisionTargetForUpdate(
            revision.getTargetSession().getSessionId()
        );
        validateApprovable(content, revision, contentSession);

        Instant reviewedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ContentSession updatedSession = contentSessionService.applyRevision(contentSession, revision);
        SessionRevision approvedRevision = sessionRevisionService.approve(
            revision,
            reviewerAssignment.getAppUser(),
            reviewedAt
        );
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            content.getRegion(),
            AuditEventTargetType.CONTENT_SESSION,
            updatedSession.getSessionId(),
            SessionRevisionStatus.PENDING.name(),
            SessionRevisionStatus.APPROVED.name(),
            AuditEventResult.SUCCESS,
            null,
            new AuditEventActor(reviewerAssignment),
            reviewedAt
        ));
        return ApproveSessionRevisionResult.from(approvedRevision, updatedSession);
    }

    private void validateApprovable(
        Content content,
        SessionRevision revision,
        ContentSession contentSession
    ) {
        if (revision.getStatus() != SessionRevisionStatus.PENDING
            || (content.getStatus() != ContentStatus.APPROVED
                && content.getStatus() != ContentStatus.PUBLISHED)
            || !contentSessionService.isBeforeStartByDatabaseTime(contentSession.getSessionId())
            || capacityHoldService.hasActiveHoldForUpdate(contentSession.getSessionId())
            || reservationService.hasRevisionBlockingReservationForUpdate(contentSession.getSessionId())) {
            throw new BusinessException(ErrorCode.SESSION_STATE_CONFLICT);
        }
    }
}
