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
    private final Clock clock;

    public EndContentReservationsUseCase(
        ContentService contentService,
        ContentSessionService contentSessionService,
        ContentLogService contentLogService,
        CapacityHoldService capacityHoldService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
        this.contentLogService = contentLogService;
        this.capacityHoldService = capacityHoldService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public EndContentReservationsResult end(
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

        if (content.getStatus() == ContentStatus.ENDED) {
            ContentLog endedLog = contentLogService.findLatestEnded(contentId);
            return EndContentReservationsResult.from(content, endedLog.getDate());
        }
        if (content.getStatus() != ContentStatus.PUBLISHED
            || contentSessionService.hasNonTerminalSessionForEnd(contentId)) {
            throw new BusinessException(ErrorCode.CONTENT_END_CONFLICT);
        }

        Instant endedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        AuditEventActor actor = new AuditEventActor(regionAdminAssignment);
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
        return EndContentReservationsResult.from(endedContent, endedAt);
    }
}
