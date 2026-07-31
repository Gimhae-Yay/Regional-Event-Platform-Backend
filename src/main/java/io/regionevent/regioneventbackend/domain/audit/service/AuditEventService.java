package io.regionevent.regioneventbackend.domain.audit.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;

@Service
public class AuditEventService {

    private static final String ACTOR_KIND = "MEMBER";
    private static final String ACTOR_ROLE = "VISITOR";

    private final AuditEventRepository auditEventRepository;

    public AuditEventService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public AuditEvent record(
        String requestId,
        CapacityHold capacityHold,
        AuditEventTargetType targetType,
        Long targetId,
        String previousState,
        String nextState,
        AuditEventResult result,
        String reasonCode,
        Instant occurredAt
    ) {
        return auditEventRepository.save(new AuditEvent(
            requestId,
            capacityHold == null ? null : capacityHold.getRegion(),
            targetType,
            targetId,
            previousState,
            nextState,
            result,
            reasonCode,
            ACTOR_KIND,
            ACTOR_ROLE,
            occurredAt
        ));
    }
}
