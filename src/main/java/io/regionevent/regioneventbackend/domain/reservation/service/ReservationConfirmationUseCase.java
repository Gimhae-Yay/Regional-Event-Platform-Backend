package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyOperation;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyAcquireResult;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyCommand;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyService;
import io.regionevent.regioneventbackend.domain.reservation.dto.ConfirmReservationResponse;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ReservationConfirmationUseCase {

    private static final String SUCCESS_RESULT_CODE = "SUCCESS";

    private final AppUserService appUserService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final CapacityHoldService capacityHoldService;
    private final ReservationService reservationService;
    private final IdempotencyService idempotencyService;
    private final ReservationConfirmationHasher reservationConfirmationHasher;
    private final RecordAuditEventUseCase recordAuditEventUseCase;

    public ReservationConfirmationUseCase(
        AppUserService appUserService,
        UserRoleAssignmentService userRoleAssignmentService,
        CapacityHoldService capacityHoldService,
        ReservationService reservationService,
        IdempotencyService idempotencyService,
        ReservationConfirmationHasher reservationConfirmationHasher,
        RecordAuditEventUseCase recordAuditEventUseCase
    ) {
        this.appUserService = appUserService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.capacityHoldService = capacityHoldService;
        this.reservationService = reservationService;
        this.idempotencyService = idempotencyService;
        this.reservationConfirmationHasher = reservationConfirmationHasher;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
    }

    @Transactional
    public ReservationConfirmationResult confirm(
        Long userId,
        String holdIdValue,
        String idempotencyKey,
        UUID requestId
    ) {
        Long holdId = toPositiveHoldId(holdIdValue);
        String validatedIdempotencyKey = validateIdempotencyKey(idempotencyKey);
        AppUser user = appUserService.findActiveUser(userId);
        AuditEventActor actor = new AuditEventActor(userRoleAssignmentService.findActiveVisitor(userId));
        IdempotencyAcquireResult acquireResult = idempotencyService.acquire(new IdempotencyCommand(
            user,
            IdempotencyOperation.RESERVATION_CONFIRM,
            reservationConfirmationHasher.hashIdempotencyKey(validatedIdempotencyKey),
            reservationConfirmationHasher.hashRequest(holdId)
        ));

        if (acquireResult instanceof IdempotencyAcquireResult.Succeeded succeeded) {
            return ReservationConfirmationResult.success(toResponse(succeeded.record().getResultReservation()));
        }
        if (acquireResult instanceof IdempotencyAcquireResult.Failed failed) {
            return ReservationConfirmationResult.failure(ErrorCode.fromCode(failed.record().getResultCode()));
        }
        if (acquireResult instanceof IdempotencyAcquireResult.KeyConflict) {
            return ReservationConfirmationResult.failure(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        if (acquireResult instanceof IdempotencyAcquireResult.InProgress) {
            return ReservationConfirmationResult.failure(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }

        return confirmNewRequest(
            (IdempotencyAcquireResult.Acquired) acquireResult,
            user,
            holdId,
            requestId,
            actor
        );
    }

    private ReservationConfirmationResult confirmNewRequest(
        IdempotencyAcquireResult.Acquired acquired,
        AppUser user,
        Long holdId,
        UUID requestId,
        AuditEventActor actor
    ) {
        capacityHoldService.findOwnedHold(holdId, user);

        try {
            CapacityHold consumedHold = capacityHoldService.consumeIfConfirmable(holdId, user.getUserId());
            Reservation reservation = reservationService.createConfirmed(consumedHold);
            idempotencyService.completeWithReservation(
                acquired.record(),
                SUCCESS_RESULT_CODE,
                reservation
            );
            recordSuccessfulAuditEvents(requestId, actor, consumedHold, reservation);
            return ReservationConfirmationResult.success(toResponse(reservation));
        } catch (ReservationConfirmationConflictException exception) {
            idempotencyService.completeWithFailure(
                acquired.record(),
                ErrorCode.RESERVATION_CONFIRM_CONFLICT.code()
            );
            return ReservationConfirmationResult.failure(ErrorCode.RESERVATION_CONFIRM_CONFLICT);
        }
    }

    private void recordSuccessfulAuditEvents(
        UUID requestId,
        AuditEventActor actor,
        CapacityHold capacityHold,
        Reservation reservation
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            capacityHold.getRegion(),
            AuditEventTargetType.CAPACITY_HOLD,
            capacityHold.getHoldId(),
            "ACTIVE",
            "CONSUMED",
            AuditEventResult.SUCCESS,
            null,
            actor,
            capacityHold.getTerminalAt()
        ));
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            reservation.getRegion(),
            AuditEventTargetType.RESERVATION,
            reservation.getReservationId(),
            null,
            "CONFIRMED",
            AuditEventResult.SUCCESS,
            null,
            actor,
            reservation.getConfirmedAt()
        ));
    }

    private ConfirmReservationResponse toResponse(Reservation reservation) {
        return ConfirmReservationResponse.from(reservationService.findById(reservation.getReservationId()));
    }

    private Long toPositiveHoldId(String value) {
        try {
            Long holdId = Long.valueOf(value);
            if (holdId <= 0) {
                throw new NumberFormatException();
            }
            return holdId;
        } catch (NumberFormatException exception) {
            throw new io.regionevent.regioneventbackend.global.error.BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new io.regionevent.regioneventbackend.global.error.BusinessException(ErrorCode.INVALID_INPUT);
        }
        return idempotencyKey;
    }
}
