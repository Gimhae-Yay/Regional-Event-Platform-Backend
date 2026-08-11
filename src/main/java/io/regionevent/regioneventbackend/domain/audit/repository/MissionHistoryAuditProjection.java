package io.regionevent.regioneventbackend.domain.audit.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;

public record MissionHistoryAuditProjection(
    Long auditEventId,
    String requestId,
    String previousState,
    String nextState,
    AuditEventResult result,
    String reasonCode,
    String actorKind,
    Long actorUserId,
    Instant occurredAt
) {
}
