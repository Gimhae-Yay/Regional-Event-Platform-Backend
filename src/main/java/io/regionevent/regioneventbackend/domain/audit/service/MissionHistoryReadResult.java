package io.regionevent.regioneventbackend.domain.audit.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;

public record MissionHistoryReadResult(
    Long auditEventId,
    String action,
    String previousStatus,
    String nextStatus,
    AuditEventResult result,
    String reasonCode,
    String actorKind,
    Long actorUserId,
    Instant recordedAt
) {
}
