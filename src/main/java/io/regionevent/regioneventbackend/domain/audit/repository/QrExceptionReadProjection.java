package io.regionevent.regioneventbackend.domain.audit.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;

public record QrExceptionReadProjection(
    Long auditEventId,
    Instant occurredAt,
    Long auditRegionId,
    AuditEventTargetType targetType,
    Long targetId,
    AuditEventResult result,
    String reasonCode,
    Long reservationId,
    Long reservationRegionId,
    Long reservationSessionId,
    Long reservationSessionRegionId,
    Long reservationContentId,
    Long reservationContentRegionId,
    Long visitId,
    Long visitRegionId,
    Long visitReservationId,
    Long visitReservationRegionId,
    Long visitSessionId,
    Long visitSessionRegionId,
    Long visitContentId,
    Long visitContentRegionId
) {
}
