package io.regionevent.regioneventbackend.domain.audit.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;

@Service
public class RecordAuditEventUseCase {

    private final AuditEventService auditEventService;
    private final AuditEventActorLinkService auditEventActorLinkService;

    public RecordAuditEventUseCase(
        AuditEventService auditEventService,
        AuditEventActorLinkService auditEventActorLinkService
    ) {
        this.auditEventService = auditEventService;
        this.auditEventActorLinkService = auditEventActorLinkService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(AuditEventCommand command) {
        AuditEvent auditEvent = auditEventService.record(command);
        auditEventActorLinkService.record(auditEvent, command.actor());
        return auditEvent;
    }
}
