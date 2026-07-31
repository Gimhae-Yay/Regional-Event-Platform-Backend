package io.regionevent.regioneventbackend.domain.audit.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventActorLink;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;

@Service
public class AuditEventActorLinkService {

    private final AuditEventActorLinkRepository auditEventActorLinkRepository;

    public AuditEventActorLinkService(AuditEventActorLinkRepository auditEventActorLinkRepository) {
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEvent auditEvent, AuditEventActor actor) {
        if (actor == null) {
            return;
        }
        auditEventActorLinkRepository.save(new AuditEventActorLink(auditEvent, actor.user()));
    }
}
