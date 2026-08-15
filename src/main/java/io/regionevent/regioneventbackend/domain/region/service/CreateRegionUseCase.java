package io.regionevent.regioneventbackend.domain.region.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreateRegionUseCase {

    private static final Pattern REGION_CODE_PATTERN = Pattern.compile(
        "^[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)*$"
    );
    private static final Set<String> ALLOWED_REASON_CODES = Set.of(
        "PILOT_REGION_ADDITION",
        "SERVICE_AREA_EXPANSION",
        "ADMINISTRATIVE_REORGANIZATION"
    );
    private static final int MAX_REGION_CODE_LENGTH = 50;
    private static final int MAX_REGION_NAME_LENGTH = 100;
    private static final int MAX_EVIDENCE_REFERENCE_LENGTH = 500;

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final RegionService regionService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public CreateRegionUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        RegionService regionService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.regionService = regionService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public CreateRegionResult create(
        Long actorUserId,
        CreateRegionCommand command,
        UUID requestId
    ) {
        validateCommand(command);
        PlatformAdminAssignment actor = platformAdminAuthorizationService
            .requireAuthorizedPlatformAdminForUpdate(actorUserId);
        Region region = regionService.createPrivateRegion(command.regionCode(), command.name());
        Instant occurredAt = clock.instant();
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            AuditEventTargetType.REGION,
            region.getRegionId(),
            null,
            "CREATED",
            AuditEventResult.SUCCESS,
            command.reasonCode(),
            command.evidenceReference(),
            new AuditEventActor(actor),
            occurredAt
        ));
        return CreateRegionResult.from(region);
    }

    private void validateCommand(CreateRegionCommand command) {
        if (command == null
            || !isValidRegionCode(command.regionCode())
            || !isValidLength(command.name(), MAX_REGION_NAME_LENGTH)
            || !ALLOWED_REASON_CODES.contains(command.reasonCode())
            || !isValidLength(command.evidenceReference(), MAX_EVIDENCE_REFERENCE_LENGTH)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean isValidRegionCode(String regionCode) {
        return regionCode != null
            && regionCode.length() <= MAX_REGION_CODE_LENGTH
            && REGION_CODE_PATTERN.matcher(regionCode).matches();
    }

    private boolean isValidLength(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    public record CreateRegionCommand(
        String regionCode,
        String name,
        String reasonCode,
        String evidenceReference
    ) {

        public CreateRegionCommand {
            regionCode = normalizeRegionCode(regionCode);
            name = normalize(name);
            reasonCode = normalize(reasonCode);
            evidenceReference = normalize(evidenceReference);
        }

        private static String normalizeRegionCode(String regionCode) {
            String normalizedRegionCode = normalize(regionCode);
            return normalizedRegionCode == null ? null : normalizedRegionCode.toUpperCase(Locale.ROOT);
        }

        private static String normalize(String value) {
            return value == null ? null : value.strip();
        }
    }
}
