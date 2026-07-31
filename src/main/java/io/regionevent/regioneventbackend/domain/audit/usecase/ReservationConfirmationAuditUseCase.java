package io.regionevent.regioneventbackend.domain.audit.usecase;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActorLinkService;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventService;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Service
public class ReservationConfirmationAuditUseCase {

    private final AuditEventService auditEventService;
    private final AuditEventActorLinkService auditEventActorLinkService;

    public ReservationConfirmationAuditUseCase(
        AuditEventService auditEventService,
        AuditEventActorLinkService auditEventActorLinkService
    ) {
        this.auditEventService = auditEventService;
        this.auditEventActorLinkService = auditEventActorLinkService;
    }

    public void recordSuccess(
        String requestId,
        AppUser actor,
        CapacityHold capacityHold,
        Reservation reservation,
        Instant occurredAt
    ) {
        recordAndLink(
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
        recordAndLink(
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
        recordAndLink(
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

    private void recordAndLink(
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
        AuditEvent auditEvent = auditEventService.record(
            requestId,
            capacityHold,
            targetType,
            targetId,
            previousState,
            nextState,
            result,
            reasonCode,
            occurredAt
        );
        auditEventActorLinkService.link(auditEvent, actor);
    }
}
