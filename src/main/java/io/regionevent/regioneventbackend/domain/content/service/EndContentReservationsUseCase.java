package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
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
public class EndContentReservationsUseCase {

    private static final String CONTENT_ENDED_INVALIDATION_REASON = "CONTENT_ENDED";

    private final ContentService contentService;
    private final RegionService regionService;
    private final ContentRevisionInvalidationService contentRevisionInvalidationService;
    private final ContentSessionService contentSessionService;
    private final ContentLogService contentLogService;
    private final CapacityHoldService capacityHoldService;
    private final ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final PublicCatalogCacheInvalidator publicCatalogCacheInvalidator;

    public EndContentReservationsUseCase(
        ContentService contentService,
        RegionService regionService,
        ContentRevisionInvalidationService contentRevisionInvalidationService,
        ContentSessionService contentSessionService,
        ContentLogService contentLogService,
        CapacityHoldService capacityHoldService,
        ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        PublicCatalogCacheInvalidator publicCatalogCacheInvalidator
    ) {
        this.contentService = contentService;
        this.regionService = regionService;
        this.contentRevisionInvalidationService = contentRevisionInvalidationService;
        this.contentSessionService = contentSessionService;
        this.contentLogService = contentLogService;
        this.capacityHoldService = capacityHoldService;
        this.expirePendingPaymentForTerminatedHoldUseCase = expirePendingPaymentForTerminatedHoldUseCase;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.publicCatalogCacheInvalidator = publicCatalogCacheInvalidator;
    }

    @Transactional
    public EndContentReservationsResult end(
        Long userId,
        Long contentId,
        UUID requestId
    ) {
        return endByRegionAdmin(userId, contentId, requestId);
    }

    @Transactional
    public EndContentReservationsResult endByRegionAdmin(
        Long userId,
        Long contentId,
        UUID requestId
    ) {
        RegionAdminAuthorizationService.AuthorizedRegionAdmin regionAdmin =
            regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(userId);
        Long regionId = contentService.findContentRegionId(contentId);
        Region region = regionService.findRegionForUpdate(regionId);
        Content content = contentService.findEndTargetForUpdate(contentId);
        UserRoleAssignment regionAdminAssignment = regionAdmin.authorize(
            region.getRegionId()
        );

        AuditEventActor actor = new AuditEventActor(regionAdminAssignment);
        String previousState = content.getStatus().name();
        try {
            if (content.getStatus() == ContentStatus.ENDED) {
                ContentLog endedLog = contentLogService.findLatestEnded(contentId);
                return EndContentReservationsResult.from(content, endedLog.getDate());
            }
            if (content.getStatus() != ContentStatus.PUBLISHED
                || contentSessionService.hasNonTerminalSessionForEnd(contentId)
                || contentSessionService.findCurrentSessionsByContentId(contentId).isEmpty()) {
                throw new BusinessException(ErrorCode.CONTENT_END_CONFLICT);
            }

            Instant endedAt = contentService.findCurrentDatabaseTime();
            Content endedContent = contentService.end(content, endedAt);
            invalidateActiveRevision(
                requestId,
                endedContent,
                actor,
                endedAt,
                ContentRevisionInvalidationReason.CONTENT_ENDED
            );
            contentLogService.recordEnded(endedContent, actor.getAppUser(), endedAt);
            capacityHoldService.invalidateAllActiveHoldsForContent(
                contentId,
                CONTENT_ENDED_INVALIDATION_REASON
            ).forEach(capacityHold -> expirePendingPaymentForTerminatedHoldUseCase.expire(
                capacityHold,
                requestId,
                actor
            ));
            recordAuditEventUseCase.record(new AuditEventCommand(
                requestId,
                region,
                AuditEventTargetType.CONTENT,
                contentId,
                ContentStatus.PUBLISHED.name(),
                ContentStatus.ENDED.name(),
                AuditEventResult.SUCCESS,
                null,
                actor,
                endedAt
            ));
            invalidatePublicContentCacheAfterCommit(endedContent);
            return EndContentReservationsResult.from(endedContent, endedAt);
        } catch (BusinessException exception) {
            recordFailure(requestId, region, contentId, previousState, exception.getErrorCode().name(), actor);
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(
                requestId,
                region,
                contentId,
                previousState,
                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                actor
            );
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<Long> findAutoEndCandidateIds() {
        return contentService.findAutoEndCandidateIds(contentSessionService.getEndTerminalStatuses());
    }

    @Transactional
    public EndContentReservationsSystemResult endBySystem(Long contentId, UUID requestId) {
        Long regionId = contentService.findContentRegionId(contentId);
        Region region = regionService.findRegionForUpdate(regionId);
        Content content = contentService.findEndTargetForUpdate(contentId);
        List<ContentSession> contentSessions = contentSessionService.findCurrentSessionsByContentId(contentId);
        if (content.getStatus() != ContentStatus.PUBLISHED
            || content.getDeletedAt() != null
            || contentSessions.isEmpty()
            || contentSessions.stream().anyMatch(
                contentSession -> !contentSessionService.getEndTerminalStatuses()
                    .contains(contentSession.getStatus())
            )) {
            return EndContentReservationsSystemResult.skipped();
        }

        String previousState = content.getStatus().name();
        try {
            Instant endedAt = contentService.findCurrentDatabaseTime();
            Content endedContent = contentService.end(content, endedAt);
            invalidateActiveRevision(
                requestId,
                endedContent,
                null,
                endedAt,
                ContentRevisionInvalidationReason.CONTENT_ENDED
            );
            contentLogService.recordEnded(endedContent, null, endedAt);
            capacityHoldService.invalidateAllActiveHoldsForContent(
                contentId,
                CONTENT_ENDED_INVALIDATION_REASON
            ).forEach(capacityHold -> expirePendingPaymentForTerminatedHoldUseCase.expire(
                capacityHold,
                requestId,
                null
            ));
            recordAuditEventUseCase.record(new AuditEventCommand(
                requestId,
                region,
                AuditEventTargetType.CONTENT,
                contentId,
                ContentStatus.PUBLISHED.name(),
                ContentStatus.ENDED.name(),
                AuditEventResult.SUCCESS,
                null,
                null,
                endedAt
            ));
            invalidatePublicContentCacheAfterCommit(endedContent);
            return EndContentReservationsSystemResult.ended(
                calculateEndingDelayMillis(contentSessions, endedAt)
            );
        } catch (RuntimeException exception) {
            recordFailure(
                requestId,
                region,
                contentId,
                previousState,
                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                null
            );
            throw exception;
        }
    }

    private long calculateEndingDelayMillis(
        List<ContentSession> contentSessions,
        Instant endedAt
    ) {
        Instant lastTerminalAt = contentSessions.stream()
            .map(this::findTerminalAt)
            .max(Instant::compareTo)
            .orElseThrow(() -> new IllegalStateException("ended content must have terminal sessions"));
        return Math.max(0L, Duration.between(lastTerminalAt, endedAt).toMillis());
    }

    private void invalidatePublicContentCacheAfterCommit(Content content) {
        publicCatalogCacheInvalidator.invalidateContentAfterCommit(
            content.getRegion().getRegionId(),
            content.getContentId(),
            content.getVersionNo()
        );
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
            actor == null ? null : actor.getAppUser(),
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

    private Instant findTerminalAt(ContentSession contentSession) {
        return switch (contentSession.getStatus()) {
            case COMPLETED -> contentSession.getCompletedAt();
            case CANCELLED -> contentSession.getCancelledAt();
            case REJECTED -> contentSession.getReviewedAt();
            default -> throw new IllegalStateException("content session must be terminal before ending content");
        };
    }

    private void recordFailure(
        UUID requestId,
        Region region,
        Long contentId,
        String previousState,
        String reasonCode,
        AuditEventActor actor
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            AuditEventTargetType.CONTENT,
            contentId,
            previousState,
            null,
            AuditEventResult.FAILURE,
            reasonCode,
            actor,
            contentService.findCurrentDatabaseTime()
        ));
    }
}
