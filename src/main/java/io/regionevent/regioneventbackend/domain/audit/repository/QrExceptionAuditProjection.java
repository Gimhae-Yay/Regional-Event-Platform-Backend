package io.regionevent.regioneventbackend.domain.audit.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;

public record QrExceptionAuditProjection(
    Long exceptionId,
    Long regionId,
    AuditEventTargetType targetType,
    Long targetId,
    AuditEventResult result,
    String reasonCode,
    Instant occurredAt
) {
}
