package io.regionevent.regioneventbackend.domain.audit.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;

@Service
public class AuditEventService {

    private static final String SYSTEM_ACTOR_KIND = "SYSTEM";
    private static final String USER_ACTOR_KIND = "USER";

    private final AuditEventRepository auditEventRepository;

    public AuditEventService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(AuditEventCommand command) {
        AuditEventActor actor = command.actor();
        return auditEventRepository.save(new AuditEvent(
            command.requestId().toString(),
            command.region(),
            command.targetType(),
            command.targetId(),
            command.previousState(),
            command.nextState(),
            command.result(),
            command.reasonCode(),
            command.reason(),
            command.evidenceReference(),
            actor == null ? SYSTEM_ACTOR_KIND : USER_ACTOR_KIND,
            actor == null ? null : actor.getRoleName(),
            command.occurredAt()
        ));
    }
}
