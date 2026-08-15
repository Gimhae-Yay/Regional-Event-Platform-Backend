package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.coupon.service.RestoreCouponUseCase;
import io.regionevent.regioneventbackend.domain.payment.dto.CreateRefundResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.service.CreateRefundUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.PaymentService;
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
    private final PaymentService paymentService;
    private final CreateRefundUseCase createRefundUseCase;
    private final RestoreCouponUseCase restoreCouponUseCase;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final TransactionTemplate transactionTemplate;

    public ReservationCancellationUseCase(
        AppUserService appUserService,
        UserRoleAssignmentService userRoleAssignmentService,
        ReservationService reservationService,
        ContentSessionService contentSessionService,
        PaymentService paymentService,
        CreateRefundUseCase createRefundUseCase,
        RestoreCouponUseCase restoreCouponUseCase,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        PlatformTransactionManager transactionManager
    ) {
        this.appUserService = appUserService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.reservationService = reservationService;
        this.contentSessionService = contentSessionService;
        this.paymentService = paymentService;
        this.createRefundUseCase = createRefundUseCase;
        this.restoreCouponUseCase = restoreCouponUseCase;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public CancelReservationResponse cancel(Long userId, Long reservationId, UUID requestId) {
        CancellationPreparation preparation = executeInTransaction(
            () -> prepareCancellation(userId, reservationId, requestId)
        );
        if (preparation.refundPreparation() == null) {
            return preparation.response();
        }
        return preparation.response().withRefund(
            createRefundUseCase.executePreparedReservationCancellationRefund(
                preparation.refundPreparation(),
                preparation.actor(),
                requestId
            )
        );
    }

    private CancellationPreparation prepareCancellation(Long userId, Long reservationId, UUID requestId) {
        validatePositiveReservationId(reservationId);
        AppUser user = appUserService.findActiveUserForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        AuditEventActor actor = new AuditEventActor(userRoleAssignmentService.findActiveVisitor(userId));
        ReservationService.ReservationCancellationLockTarget lockTarget = reservationService
            .findCancellationLockTarget(reservationId, user);
        contentSessionService.lockForUpdate(lockTarget.sessionId());

        Reservation reservation = reservationService.findOwnedReservationForUpdate(reservationId, user);
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return new CancellationPreparation(
                CancelReservationResponse.from(reservation, findExistingRefund(reservation)),
                null,
                actor
            );
        }
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throwCancellationConflict(requestId, actor, reservation);
        }
        int quantity = reservation.getCapacityHold().getQuantity();
        if (!reservationService.cancelIfCancellable(reservationId, userId)) {
            Reservation currentReservation = reservationService.findOwnedReservationForUpdate(reservationId, user);
            if (currentReservation.getStatus() == ReservationStatus.CANCELLED) {
                return new CancellationPreparation(
                    CancelReservationResponse.from(currentReservation, findExistingRefund(currentReservation)),
                    null,
                    actor
                );
            }
            throwCancellationConflict(requestId, actor, currentReservation);
        }

        contentSessionService.restoreCapacity(
            lockTarget.sessionId(),
            quantity
        );
        Reservation cancelledReservation = reservationService.findOwnedReservation(reservationId, user);
        recordSuccessfulAuditEvent(requestId, actor, cancelledReservation);
        return new CancellationPreparation(
            CancelReservationResponse.from(cancelledReservation),
            prepareRefundIfRequired(cancelledReservation, actor, requestId),
            actor
        );
    }

    private CreateRefundUseCase.ReservationCancellationRefundPreparation prepareRefundIfRequired(
        Reservation reservation,
        AuditEventActor actor,
        UUID requestId
    ) {
        Payment payment = paymentService.findByReservationId(reservation.getReservationId())
            .orElse(null);
        if (payment == null) {
            restoreCouponUseCase.restoreForReservationCancellation(reservation, requestId, actor);
            return null;
        }
        if (payment.getStatus() != PaymentStatus.APPROVED
            && payment.getStatus() != PaymentStatus.DISCREPANT) {
            return null;
        }
        return createRefundUseCase.prepareForReservationCancellation(
            payment.getPaymentId(),
            actor,
            requestId
        );
    }

    private CreateRefundResponse findExistingRefund(Reservation reservation) {
        Payment payment = paymentService.findByReservationId(reservation.getReservationId())
            .orElse(null);
        if (payment == null) {
            return null;
        }
        return createRefundUseCase.findForReservationCancellation(payment.getPaymentId());
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
        log.atWarn()
            .addKeyValue("requestId", requestId)
            .addKeyValue("reservationId", reservation.getReservationId())
            .addKeyValue("errorCode", ErrorCode.RESERVATION_CANCEL_CONFLICT.code())
            .log("Reservation cancellation rejected");
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

    private <T> T executeInTransaction(java.util.function.Supplier<T> action) {
        T result = transactionTemplate.execute(status -> action.get());
        return result;
    }

    private record CancellationPreparation(
        CancelReservationResponse response,
        CreateRefundUseCase.ReservationCancellationRefundPreparation refundPreparation,
        AuditEventActor actor
    ) {
    }
}
