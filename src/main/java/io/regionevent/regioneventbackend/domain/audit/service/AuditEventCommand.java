package io.regionevent.regioneventbackend.domain.audit.service;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.region.entity.Region;

public record AuditEventCommand(
    UUID requestId,
    Region region,
    AuditEventTargetType targetType,
    Long targetId,
    String previousState,
    String nextState,
    AuditEventResult result,
    String reasonCode,
    String evidenceReference,
    AuditEventActor actor,
    Instant occurredAt
) {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final int MAX_STATE_LENGTH = 30;
    private static final int MAX_REASON_CODE_LENGTH = 100;

    public AuditEventCommand {
        if (requestId == null) {
            throw new IllegalArgumentException("requestId must not be null");
        }
        if (region != null && region.getRegionId() == null) {
            throw new IllegalArgumentException("region must be persisted");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("targetType must not be null");
        }
        if (targetId != null && targetId <= 0) {
            throw new IllegalArgumentException("targetId must be positive");
        }
        validateOptionalCode(previousState, MAX_STATE_LENGTH, "previousState");
        validateOptionalCode(nextState, MAX_STATE_LENGTH, "nextState");
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        validateSuccessfulStateTransition(result, targetId, nextState);
        validateFailedAuditReasonCode(result, reasonCode);
        validateOptionalCode(reasonCode, MAX_REASON_CODE_LENGTH, "reasonCode");
        evidenceReference = normalizeOptionalEvidenceReference(evidenceReference);
        validatePrivilegedChangeEvidenceReference(targetType, evidenceReference);
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
    }

    public AuditEventCommand(
        UUID requestId,
        Region region,
        AuditEventTargetType targetType,
        Long targetId,
        String previousState,
        String nextState,
        AuditEventResult result,
        String reasonCode,
        AuditEventActor actor,
        Instant occurredAt
    ) {
        this(
            requestId,
            region,
            targetType,
            targetId,
            previousState,
            nextState,
            result,
            reasonCode,
            null,
            actor,
            occurredAt
        );
    }

    private static void validateSuccessfulStateTransition(
        AuditEventResult result,
        Long targetId,
        String nextState
    ) {
        if (result != AuditEventResult.SUCCESS) {
            return;
        }
        if (targetId == null) {
            throw new IllegalArgumentException("success audit event targetId must not be null");
        }
        if (nextState == null) {
            throw new IllegalArgumentException("success audit event nextState must not be null");
        }
    }

    private static void validateFailedAuditReasonCode(
        AuditEventResult result,
        String reasonCode
    ) {
        if (result == AuditEventResult.FAILURE && (reasonCode == null || reasonCode.isBlank())) {
            throw new IllegalArgumentException("failed audit event reasonCode must not be null or blank");
        }
    }

    private static void validateOptionalCode(
        String value,
        int maxLength,
        String fieldName
    ) {
        if (value == null) {
            return;
        }
        if (value.isBlank() || value.length() > maxLength || !CODE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must be an uppercase code");
        }
    }

    private static String normalizeOptionalEvidenceReference(String evidenceReference) {
        if (evidenceReference == null) {
            return null;
        }

        String normalizedEvidenceReference = evidenceReference.strip();
        if (normalizedEvidenceReference.isEmpty() || normalizedEvidenceReference.length() > 500) {
            throw new IllegalArgumentException("evidenceReference must be between 1 and 500 characters");
        }
        return normalizedEvidenceReference;
    }

    private static void validatePrivilegedChangeEvidenceReference(
        AuditEventTargetType targetType,
        String evidenceReference
    ) {
        if ((targetType == AuditEventTargetType.PLATFORM_ADMIN_ASSIGNMENT
            || targetType == AuditEventTargetType.USER_ROLE_ASSIGNMENT)
            && evidenceReference == null) {
            throw new IllegalArgumentException(
                "privileged change audit event evidenceReference must not be null"
            );
        }
    }
}
