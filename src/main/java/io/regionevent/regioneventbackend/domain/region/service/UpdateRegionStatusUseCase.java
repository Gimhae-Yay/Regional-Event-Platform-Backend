package io.regionevent.regioneventbackend.domain.region.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class UpdateRegionStatusUseCase {

    private static final Set<String> PUBLIC_REASON_CODES = Set.of(
        "REGION_LAUNCH",
        "REGION_REOPEN"
    );
    private static final Set<String> PRIVATE_REASON_CODES = Set.of(
        "REGION_PREPARATION",
        "ADMINISTRATIVE_REORGANIZATION"
    );

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final RegionService regionService;
    private final ContentService contentService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Clock clock;

    public UpdateRegionStatusUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        RegionService regionService,
        ContentService contentService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        Clock clock
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.regionService = regionService;
        this.contentService = contentService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public UpdateRegionStatusResult update(
        Long actorUserId,
        Long regionId,
        UpdateRegionStatusCommand command,
        UUID requestId
    ) {
        validateCommand(command);
        PlatformAdminAssignment actor = platformAdminAuthorizationService
            .requireAuthorizedPlatformAdmin(actorUserId);
        Region region = regionService.findRegionForUpdate(regionId);
        if (region.isPublic() == command.isPublic()) {
            return UpdateRegionStatusResult.from(region);
        }

        if (!command.isPublic() && contentService.hasUndeletedContentInRegion(region.getRegionId())) {
            recordAvailabilityConflict(requestId, region, actor, command.evidenceReference());
            throw new BusinessException(ErrorCode.REGION_AVAILABILITY_CONFLICT);
        }

        boolean previousPublic = region.isPublic();
        Region updatedRegion = regionService.changeVisibility(region, command.isPublic());
        recordSuccessfulVisibilityChange(
            requestId,
            updatedRegion,
            actor,
            previousPublic,
            command
        );
        return UpdateRegionStatusResult.from(updatedRegion);
    }

    private void validateCommand(UpdateRegionStatusCommand command) {
        if (command == null
            || !isAllowedReasonCode(command.isPublic(), command.reasonCode())
            || !isValidEvidenceReference(command.evidenceReference())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean isAllowedReasonCode(boolean isPublic, String reasonCode) {
        Set<String> allowedReasonCodes = isPublic ? PUBLIC_REASON_CODES : PRIVATE_REASON_CODES;
        return allowedReasonCodes.contains(reasonCode);
    }

    private boolean isValidEvidenceReference(String evidenceReference) {
        return evidenceReference != null
            && !evidenceReference.isBlank()
            && evidenceReference.length() <= 500;
    }

    private void recordAvailabilityConflict(
        UUID requestId,
        Region region,
        PlatformAdminAssignment actor,
        String evidenceReference
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            AuditEventTargetType.REGION,
            region.getRegionId(),
            toAuditState(region.isPublic()),
            null,
            AuditEventResult.FAILURE,
            ErrorCode.REGION_AVAILABILITY_CONFLICT.code(),
            evidenceReference,
            new AuditEventActor(actor),
            clock.instant()
        ));
    }

    private void recordSuccessfulVisibilityChange(
        UUID requestId,
        Region region,
        PlatformAdminAssignment actor,
        boolean previousPublic,
        UpdateRegionStatusCommand command
    ) {
        Instant occurredAt = clock.instant();
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            AuditEventTargetType.REGION,
            region.getRegionId(),
            toAuditState(previousPublic),
            toAuditState(command.isPublic()),
            AuditEventResult.SUCCESS,
            command.reasonCode(),
            command.evidenceReference(),
            new AuditEventActor(actor),
            occurredAt
        ));
    }

    private String toAuditState(boolean isPublic) {
        return isPublic ? "TRUE" : "FALSE";
    }

    public record UpdateRegionStatusCommand(
        boolean isPublic,
        String reasonCode,
        String evidenceReference
    ) {

        public UpdateRegionStatusCommand {
            reasonCode = normalize(reasonCode);
            evidenceReference = normalize(evidenceReference);
        }

        private static String normalize(String value) {
            return value == null ? null : value.strip();
        }
    }
}
