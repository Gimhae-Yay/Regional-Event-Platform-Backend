package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventActorLink;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Service
public class ReservationConfirmationAuditService {

    private static final String ACTOR_KIND = "MEMBER";
    private static final String ACTOR_ROLE = "VISITOR";

    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;

    public ReservationConfirmationAuditService(
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository
    ) {
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
    }

    public void recordSuccess(
        String requestId,
        AppUser actor,
        CapacityHold capacityHold,
        Reservation reservation,
        Instant occurredAt
    ) {
        saveEventWithActor(
            requestId,
            actor,
            capacityHold,
            AuditEventTargetType.CAPACITY_HOLD,
            capacityHold.getHoldId(),
            "ACTIVE",
            "CONSUMED",
            AuditEventResult.SUCCESS,
            null,
            occurredAt
        );
        saveEventWithActor(
            requestId,
            actor,
            capacityHold,
            AuditEventTargetType.RESERVATION,
            reservation.getReservationId(),
            null,
            "CONFIRMED",
            AuditEventResult.SUCCESS,
            null,
            occurredAt
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
        String requestId,
        AppUser actor,
        CapacityHold capacityHold,
        String reasonCode,
        Instant occurredAt
    ) {
        saveEventWithActor(
            requestId,
            actor,
            capacityHold,
            AuditEventTargetType.CAPACITY_HOLD,
            capacityHold == null ? null : capacityHold.getHoldId(),
            capacityHold == null ? null : capacityHold.getStatus().name(),
            null,
            AuditEventResult.FAILURE,
            reasonCode,
            occurredAt
        );
    }

    private void saveEventWithActor(
        String requestId,
        AppUser actor,
        CapacityHold capacityHold,
        AuditEventTargetType targetType,
        Long targetId,
        String previousState,
        String nextState,
        AuditEventResult result,
        String reasonCode,
        Instant occurredAt
    ) {
        AuditEvent auditEvent = auditEventRepository.save(new AuditEvent(
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
        auditEventActorLinkRepository.save(new AuditEventActorLink(auditEvent, actor));
    }
}
