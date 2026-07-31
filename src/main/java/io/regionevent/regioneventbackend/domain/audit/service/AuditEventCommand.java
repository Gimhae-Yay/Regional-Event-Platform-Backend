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
        validateOptionalCode(reasonCode, MAX_REASON_CODE_LENGTH, "reasonCode");
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
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
}
