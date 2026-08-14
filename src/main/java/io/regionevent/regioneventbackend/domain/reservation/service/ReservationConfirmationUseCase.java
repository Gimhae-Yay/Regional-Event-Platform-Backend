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
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailureAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyOperation;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyAcquireResult;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyCommand;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.reservation.dto.ConfirmReservationResponse;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ReservationConfirmationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReservationConfirmationUseCase.class);
    private static final String SUCCESS_RESULT_CODE = "SUCCESS";

    private final AppUserService appUserService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final ContentService contentService;
    private final ContentSessionService contentSessionService;
    private final CapacityHoldService capacityHoldService;
    private final ReservationService reservationService;
    private final IdempotencyService idempotencyService;
    private final ReservationConfirmationHasher reservationConfirmationHasher;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailureAuditEventUseCase recordFailureAuditEventUseCase;

    public ReservationConfirmationUseCase(
        AppUserService appUserService,
        UserRoleAssignmentService userRoleAssignmentService,
        ContentService contentService,
        ContentSessionService contentSessionService,
        CapacityHoldService capacityHoldService,
        ReservationService reservationService,
        IdempotencyService idempotencyService,
        ReservationConfirmationHasher reservationConfirmationHasher,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailureAuditEventUseCase recordFailureAuditEventUseCase
    ) {
        this.appUserService = appUserService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
        this.capacityHoldService = capacityHoldService;
        this.reservationService = reservationService;
        this.idempotencyService = idempotencyService;
        this.reservationConfirmationHasher = reservationConfirmationHasher;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailureAuditEventUseCase = recordFailureAuditEventUseCase;
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
        AppUser user = appUserService.findActiveUserForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        AuditEventActor actor = new AuditEventActor(userRoleAssignmentService.findActiveVisitor(userId));
        CapacityHold capacityHold = capacityHoldService.findOwnedHold(holdId, user);
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
            recordFailure(requestId, actor, holdId, null, null, ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            return ReservationConfirmationResult.failure(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        if (acquireResult instanceof IdempotencyAcquireResult.InProgress) {
            recordFailure(requestId, actor, holdId, null, null, ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
            return ReservationConfirmationResult.failure(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }

        return confirmNewRequest(
            (IdempotencyAcquireResult.Acquired) acquireResult,
            user,
            capacityHold,
            requestId,
            actor
        );
    }

    private ReservationConfirmationResult confirmNewRequest(
        IdempotencyAcquireResult.Acquired acquired,
        AppUser user,
        CapacityHold capacityHold,
        UUID requestId,
        AuditEventActor actor
    ) {
        try {
            Long sessionId = capacityHold.getContentSession().getSessionId();
            Long contentId = capacityHold.getContentSession().getContent().getContentId();
            if (!contentService.lockPublishedReservationTarget(contentId)
                || !contentSessionService.lockConfirmableReservationTarget(sessionId)) {
                throw new ReservationConfirmationConflictException();
            }
            if (reservationService.existsByCapacityHoldId(capacityHold.getHoldId())) {
                throw new ReservationConfirmationConflictException();
            }
            CapacityHold consumedHold = capacityHoldService.consumeIfConfirmable(
                capacityHold.getHoldId(),
                user.getUserId()
            );
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
            recordFailure(
                requestId,
                actor,
                capacityHold.getHoldId(),
                capacityHold.getRegion(),
                "ACTIVE",
                ErrorCode.RESERVATION_CONFIRM_CONFLICT
            );
            return ReservationConfirmationResult.failure(ErrorCode.RESERVATION_CONFIRM_CONFLICT);
        }
    }

    private void recordFailure(
        UUID requestId,
        AuditEventActor actor,
        Long holdId,
        Region region,
        String previousState,
        ErrorCode errorCode
    ) {
        recordFailureAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            AuditEventTargetType.CAPACITY_HOLD,
            holdId,
            previousState,
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            actor,
            Instant.now()
        ));
        log.warn(
            "Reservation confirmation rejected. requestId={}, userId={}, holdId={}, errorCode={}",
            requestId,
            actor.getAppUser().getUserId(),
            holdId,
            errorCode.code()
        );
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
