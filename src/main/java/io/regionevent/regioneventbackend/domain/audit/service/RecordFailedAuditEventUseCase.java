package io.regionevent.regioneventbackend.domain.audit.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;

@Service
public class RecordFailedAuditEventUseCase {

    private final AuditEventService auditEventService;
    private final AuditEventActorLinkService auditEventActorLinkService;

    public RecordFailedAuditEventUseCase(
        AuditEventService auditEventService,
        AuditEventActorLinkService auditEventActorLinkService
    ) {
        this.auditEventService = auditEventService;
        this.auditEventActorLinkService = auditEventActorLinkService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent record(AuditEventCommand command) {
        if (command.result() != AuditEventResult.FAILURE) {
            throw new IllegalArgumentException("failed audit event must have FAILURE result");
        }
        AuditEvent auditEvent = auditEventService.record(command);
        auditEventActorLinkService.record(auditEvent, command.actor());
        return auditEvent;
    }
}
