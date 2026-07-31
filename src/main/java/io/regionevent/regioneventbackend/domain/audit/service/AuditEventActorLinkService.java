package io.regionevent.regioneventbackend.domain.audit.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventActorLink;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Service
public class AuditEventActorLinkService {

    private final AuditEventActorLinkRepository auditEventActorLinkRepository;

    public AuditEventActorLinkService(AuditEventActorLinkRepository auditEventActorLinkRepository) {
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
    }

    public void link(AuditEvent auditEvent, AppUser actor) {
        auditEventActorLinkRepository.save(new AuditEventActorLink(auditEvent, actor));
    }
}
