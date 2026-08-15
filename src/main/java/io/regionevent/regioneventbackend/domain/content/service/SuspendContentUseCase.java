package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionInvalidationReason;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.payment.service.ExpirePendingPaymentForTerminatedHoldUseCase;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class SuspendContentUseCase {

    private static final Logger log = LoggerFactory.getLogger(SuspendContentUseCase.class);
    private static final String CONTENT_SUSPENDED_INVALIDATION_REASON = "CONTENT_SUSPENDED";

    private final ContentService contentService;
    private final ContentRevisionInvalidationService contentRevisionInvalidationService;
    private final ContentSessionService contentSessionService;
    private final ContentLogService contentLogService;
    private final CapacityHoldService capacityHoldService;
    private final ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RegionService regionService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Clock clock;

    public SuspendContentUseCase(
        ContentService contentService,
        ContentRevisionInvalidationService contentRevisionInvalidationService,
        ContentSessionService contentSessionService,
        ContentLogService contentLogService,
        CapacityHoldService capacityHoldService,
        ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RegionService regionService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        Clock clock
    ) {
        this.contentService = contentService;
        this.contentRevisionInvalidationService = contentRevisionInvalidationService;
        this.contentSessionService = contentSessionService;
        this.contentLogService = contentLogService;
        this.capacityHoldService = capacityHoldService;
        this.expirePendingPaymentForTerminatedHoldUseCase = expirePendingPaymentForTerminatedHoldUseCase;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.regionService = regionService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public SuspendContentResult suspend(
        Long userId,
        Long contentId,
        String reason,
        UUID requestId
    ) {
        String normalizedReason = normalizeReason(reason);
        RegionAdminAuthorizationService.AuthorizedRegionAdmin regionAdmin =
            regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(userId);
        Long regionId = contentService.findSuspendTargetRegionId(contentId);
        Region region = regionService.findRegionForUpdate(regionId);
        Content content = contentService.findSuspendTargetForUpdate(contentId);
        UserRoleAssignment regionAdminAssignment = regionAdmin.authorize(
            regionId
        );
        if (content.getStatus() != ContentStatus.PUBLISHED) {
            recordFailedSuspension(
                requestId,
                content,
                content.getStatus().name(),
                ErrorCode.CONTENT_SUSPEND_CONFLICT
            );
            throw new BusinessException(ErrorCode.CONTENT_SUSPEND_CONFLICT);
        }

        AuditEventActor actor = new AuditEventActor(regionAdminAssignment);
        Instant suspendedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        try {
            Content suspendedContent = contentService.suspend(content, suspendedAt);
            invalidateActiveRevision(
                requestId,
                suspendedContent,
                actor,
                suspendedAt,
                ContentRevisionInvalidationReason.CONTENT_SUSPENDED
            );
            contentSessionService.lockSuspendTargetsForUpdate(contentId);
            ContentLog suspendedLog = contentLogService.recordSuspended(
                suspendedContent,
                actor.getAppUser(),
                suspendedAt,
                normalizedReason
            );
            capacityHoldService.invalidateAllActiveHoldsForContent(
                contentId,
                CONTENT_SUSPENDED_INVALIDATION_REASON
            ).forEach(capacityHold -> expirePendingPaymentForTerminatedHoldUseCase.expire(
                capacityHold,
                requestId,
                actor
            ));
            recordSuccessfulSuspension(requestId, actor, suspendedContent, suspendedAt);
            return SuspendContentResult.from(suspendedContent, suspendedLog);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.CONTENT_SUSPEND_CONFLICT) {
                recordFailedSuspension(
                    requestId,
                    content,
                    ContentStatus.PUBLISHED.name(),
                    ErrorCode.CONTENT_SUSPEND_CONFLICT
                );
            }
            throw exception;
        } catch (RuntimeException exception) {
            recordFailedSuspension(
                requestId,
                content,
                ContentStatus.PUBLISHED.name(),
                ErrorCode.INTERNAL_SERVER_ERROR
            );
            throw exception;
        }
    }

    private void recordSuccessfulSuspension(
        UUID requestId,
        AuditEventActor actor,
        Content content,
        Instant suspendedAt
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            content.getRegion(),
            AuditEventTargetType.CONTENT,
            content.getContentId(),
            ContentStatus.PUBLISHED.name(),
            ContentStatus.SUSPENDED.name(),
            AuditEventResult.SUCCESS,
            null,
            actor,
            suspendedAt
        ));
    }

    private void invalidateActiveRevision(
        UUID requestId,
        Content content,
        AuditEventActor actor,
        Instant invalidatedAt,
        ContentRevisionInvalidationReason reason
    ) {
        contentRevisionInvalidationService.invalidateActiveRevisionForContent(
            content.getContentId(),
            actor.getAppUser(),
            invalidatedAt,
            reason
        ).ifPresent(revision -> recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            content.getRegion(),
            AuditEventTargetType.CONTENT,
            content.getContentId(),
            ContentRevisionStatus.EDIT_REQUESTED.name(),
            ContentRevisionStatus.EDIT_INVALIDATED.name(),
            AuditEventResult.SUCCESS,
            reason.name(),
            actor,
            invalidatedAt
        )));
    }

    private void recordFailedSuspension(
        UUID requestId,
        Content content,
        String previousState,
        ErrorCode errorCode
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            content.getRegion(),
            AuditEventTargetType.CONTENT,
            content.getContentId(),
            previousState,
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            null,
            clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
        log.warn(
            "Content suspension rejected. requestId={}, contentId={}, errorCode={}",
            requestId,
            content.getContentId(),
            errorCode.code()
        );
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return reason.strip();
    }
}
