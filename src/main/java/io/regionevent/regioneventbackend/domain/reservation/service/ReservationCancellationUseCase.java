package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.reservation.dto.CancelReservationResponse;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ReservationCancellationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReservationCancellationUseCase.class);
    private static final String USER_REQUEST_REASON = "USER_REQUEST";

    private final AppUserService appUserService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final ReservationService reservationService;
    private final ContentSessionService contentSessionService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;

    public ReservationCancellationUseCase(
        AppUserService appUserService,
        UserRoleAssignmentService userRoleAssignmentService,
        ReservationService reservationService,
        ContentSessionService contentSessionService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase
    ) {
        this.appUserService = appUserService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.reservationService = reservationService;
        this.contentSessionService = contentSessionService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
    }

    @Transactional
    public CancelReservationResponse cancel(Long userId, Long reservationId, UUID requestId) {
        validatePositiveReservationId(reservationId);
        AppUser user = appUserService.findActiveUserForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        AuditEventActor actor = new AuditEventActor(userRoleAssignmentService.findActiveVisitor(userId));
        ReservationService.ReservationCancellationLockTarget lockTarget = reservationService
            .findCancellationLockTarget(reservationId, user);
        contentSessionService.lockForUpdate(lockTarget.sessionId());

        Reservation reservation = reservationService.findOwnedReservationForUpdate(reservationId, user);
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return CancelReservationResponse.from(reservation);
        }
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throwCancellationConflict(requestId, actor, reservation);
        }
        int quantity = reservation.getCapacityHold().getQuantity();
        if (!reservationService.cancelIfCancellable(reservationId, userId)) {
            Reservation currentReservation = reservationService.findOwnedReservationForUpdate(reservationId, user);
            if (currentReservation.getStatus() == ReservationStatus.CANCELLED) {
                return CancelReservationResponse.from(currentReservation);
            }
            throwCancellationConflict(requestId, actor, currentReservation);
        }

        contentSessionService.restoreCapacity(
            lockTarget.sessionId(),
            quantity
        );
        Reservation cancelledReservation = reservationService.findOwnedReservation(reservationId, user);
        recordSuccessfulAuditEvent(requestId, actor, cancelledReservation);
        return CancelReservationResponse.from(cancelledReservation);
    }

    private void validatePositiveReservationId(Long reservationId) {
        if (reservationId == null || reservationId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void throwCancellationConflict(
        UUID requestId,
        AuditEventActor actor,
        Reservation reservation
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            reservation.getRegion(),
            AuditEventTargetType.RESERVATION,
            reservation.getReservationId(),
            reservation.getStatus().name(),
            null,
            AuditEventResult.FAILURE,
            ErrorCode.RESERVATION_CANCEL_CONFLICT.code(),
            actor,
            Instant.now()
        ));
        log.warn(
            "Reservation cancellation rejected. requestId={}, userId={}, reservationId={}, errorCode={}",
            requestId,
            actor.getAppUser().getUserId(),
            reservation.getReservationId(),
            ErrorCode.RESERVATION_CANCEL_CONFLICT.code()
        );
        throw new BusinessException(ErrorCode.RESERVATION_CANCEL_CONFLICT);
    }

    private void recordSuccessfulAuditEvent(
        UUID requestId,
        AuditEventActor actor,
        Reservation reservation
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            reservation.getRegion(),
            AuditEventTargetType.RESERVATION,
            reservation.getReservationId(),
            ReservationStatus.CONFIRMED.name(),
            ReservationStatus.CANCELLED.name(),
            AuditEventResult.SUCCESS,
            USER_REQUEST_REASON,
            actor,
            reservation.getCancelledAt()
        ));
    }
}
