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
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class EndContentReservationsUseCase {

    private static final String CONTENT_ENDED_INVALIDATION_REASON = "CONTENT_ENDED";

    private final ContentService contentService;
    private final ContentSessionService contentSessionService;
    private final ContentLogService contentLogService;
    private final CapacityHoldService capacityHoldService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final PublicCatalogCacheInvalidator publicCatalogCacheInvalidator;

    public EndContentReservationsUseCase(
        ContentService contentService,
        ContentSessionService contentSessionService,
        ContentLogService contentLogService,
        CapacityHoldService capacityHoldService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        PublicCatalogCacheInvalidator publicCatalogCacheInvalidator
    ) {
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
        this.contentLogService = contentLogService;
        this.capacityHoldService = capacityHoldService;
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
        Content content = contentService.findEndTargetForUpdate(contentId);
        Region region = content.getRegion();
        UserRoleAssignment regionAdminAssignment = regionAdminAuthorizationService.authorize(
            userId,
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
            contentLogService.recordEnded(endedContent, actor.getAppUser(), endedAt);
            capacityHoldService.invalidateAllActiveHoldsForContent(
                contentId,
                CONTENT_ENDED_INVALIDATION_REASON
            );
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
            contentLogService.recordEnded(endedContent, null, endedAt);
            capacityHoldService.invalidateAllActiveHoldsForContent(
                contentId,
                CONTENT_ENDED_INVALIDATION_REASON
            );
            recordAuditEventUseCase.record(new AuditEventCommand(
                requestId,
                content.getRegion(),
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
                content.getRegion(),
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
