package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.coupon.service.RestoreCouponUseCase;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneResponseException;
import io.regionevent.regioneventbackend.domain.payment.service.CreateRefundUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.ExpirePendingPaymentForTerminatedHoldUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.PaymentService;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CancelContentSessionUseCase {

    private static final int MAX_CANCELLATION_REASON_LENGTH = 500;
    private static final String OPERATOR_SESSION_CANCEL_REASON = "OPERATOR_SESSION_CANCEL";

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final ContentSessionService contentSessionService;
    private final CapacityHoldService capacityHoldService;
    private final ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase;
    private final ReservationService reservationService;
    private final PaymentService paymentService;
    private final CreateRefundUseCase createRefundUseCase;
    private final RestoreCouponUseCase restoreCouponUseCase;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public CancelContentSessionUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        ContentSessionService contentSessionService,
        CapacityHoldService capacityHoldService,
        ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase,
        ReservationService reservationService,
        PaymentService paymentService,
        CreateRefundUseCase createRefundUseCase,
        RestoreCouponUseCase restoreCouponUseCase,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock,
        PlatformTransactionManager transactionManager
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.contentSessionService = contentSessionService;
        this.capacityHoldService = capacityHoldService;
        this.expirePendingPaymentForTerminatedHoldUseCase = expirePendingPaymentForTerminatedHoldUseCase;
        this.reservationService = reservationService;
        this.paymentService = paymentService;
        this.createRefundUseCase = createRefundUseCase;
        this.restoreCouponUseCase = restoreCouponUseCase;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public CancelContentSessionResult cancel(
        Long operatorUserId,
        Long sessionId,
        String cancellationReason,
        UUID requestId
    ) {
        CancellationPreparation preparation = executeInTransaction(() -> prepareCancellation(
            operatorUserId,
            sessionId,
            cancellationReason,
            requestId
        ));
        preparation.refunds().forEach(refund -> executeRefund(refund, preparation.actor(), requestId));
        return preparation.result();
    }

    private CancellationPreparation prepareCancellation(
        Long operatorUserId,
        Long sessionId,
        String cancellationReason,
        UUID requestId
    ) {
        String validatedReason = validateCancellationReason(cancellationReason);
        OperatorAuthorizationService.AuthorizedOperator operator = operatorAuthorizationService
            .requireAuthorizedOperator(operatorUserId);
        ContentSession contentSession = contentSessionService.findCancelTargetForUpdate(sessionId);
        validateOwnership(operator, contentSession);

        Instant cancelledAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ContentSession cancelledSession = contentSessionService.cancel(
            contentSession,
            operator.user(),
            cancelledAt,
            validatedReason
        );
        List<CapacityHoldService.TerminatedCapacityHold> terminatedHolds = capacityHoldService
            .invalidateActiveHoldsForSession(
            cancelledSession,
            validatedReason,
            cancelledAt
        );
        AuditEventActor actor = new AuditEventActor(operator.roleAssignment());
        terminatedHolds.forEach(capacityHold -> expirePendingPaymentForTerminatedHoldUseCase.expire(
            capacityHold,
            requestId,
            actor
        ));
        ReservationService.SessionReservationCancellationResult reservationCancellation = reservationService
            .cancelUncheckedInReservationsForSession(
                cancelledSession,
                validatedReason,
                cancelledAt
            );
        int releasedQuantity = terminatedHolds.stream()
            .mapToInt(CapacityHoldService.TerminatedCapacityHold::quantity)
            .sum() + reservationCancellation.releasedQuantity();
        contentSessionService.releaseCapacity(cancelledSession, releasedQuantity);
        List<CreateRefundUseCase.ReservationCancellationRefundPreparation> refunds = prepareCouponReversals(
            reservationCancellation.cancelledReservations(),
            actor,
            requestId
        );
        recordSuccessfulAuditEvent(requestId, actor, cancelledSession, cancelledAt);

        return new CancellationPreparation(
            new CancelContentSessionResult(
                cancelledSession.getSessionId(),
                cancelledSession.getStatus(),
                cancelledSession.getCancellationReason(),
                cancelledSession.getCancelledAt()
            ),
            refunds,
            actor
        );
    }

    private List<CreateRefundUseCase.ReservationCancellationRefundPreparation> prepareCouponReversals(
        List<Reservation> cancelledReservations,
        AuditEventActor actor,
        UUID requestId
    ) {
        List<CreateRefundUseCase.ReservationCancellationRefundPreparation> refunds = new ArrayList<>();
        for (Reservation reservation : cancelledReservations) {
            CreateRefundUseCase.ReservationCancellationRefundPreparation refund = prepareCouponReversal(
                reservation,
                actor,
                requestId
            );
            if (refund != null) {
                refunds.add(refund);
            }
        }
        return List.copyOf(refunds);
    }

    private CreateRefundUseCase.ReservationCancellationRefundPreparation prepareCouponReversal(
        Reservation reservation,
        AuditEventActor actor,
        UUID requestId
    ) {
        Payment payment = paymentService.findByReservationId(reservation.getReservationId()).orElse(null);
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

    private void executeRefund(
        CreateRefundUseCase.ReservationCancellationRefundPreparation refund,
        AuditEventActor actor,
        UUID requestId
    ) {
        try {
            createRefundUseCase.executePreparedReservationCancellationRefund(refund, actor, requestId);
        } catch (PortOneResponseException exception) {
            // 응답 오류는 환불을 DISCREPANT로 확정한 뒤 전파되므로 완료된 회차 취소에는 영향을 주지 않는다.
        }
    }

    private <T> T executeInTransaction(java.util.function.Supplier<T> action) {
        T result = transactionTemplate.execute(status -> action.get());
        if (result == null) {
            throw new IllegalStateException("transaction result must not be null");
        }
        return result;
    }

    private void validateOwnership(
        OperatorAuthorizationService.AuthorizedOperator operator,
        ContentSession contentSession
    ) {
        if (!contentSession.getRegion().getRegionId().equals(operator.region().getRegionId())
            || !contentSession.getContent().isOwnedBy(operator.user().getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String validateCancellationReason(String cancellationReason) {
        if (cancellationReason == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String trimmedReason = cancellationReason.trim();
        if (trimmedReason.isBlank() || trimmedReason.length() > MAX_CANCELLATION_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return trimmedReason;
    }

    private void recordSuccessfulAuditEvent(
        UUID requestId,
        AuditEventActor actor,
        ContentSession contentSession,
        Instant cancelledAt
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            contentSession.getRegion(),
            AuditEventTargetType.CONTENT_SESSION,
            contentSession.getSessionId(),
            "SCHEDULED",
            "CANCELLED",
            AuditEventResult.SUCCESS,
            OPERATOR_SESSION_CANCEL_REASON,
            actor,
            cancelledAt
        ));
    }

    private record CancellationPreparation(
        CancelContentSessionResult result,
        List<CreateRefundUseCase.ReservationCancellationRefundPreparation> refunds,
        AuditEventActor actor
    ) {
    }
}
