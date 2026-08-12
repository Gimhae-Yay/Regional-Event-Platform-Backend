package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemption;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemptionStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponRedemptionService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptOutcomeKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundFailureReasonCode;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneLookupException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneNoResponseException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;

@Service
public class RecoverPendingRefundAttemptsUseCase {

    private static final Duration RECOVERY_DELAY = Duration.ofMinutes(1);
    private static final String COUPON_RESTORE_REASON = "REFUND_SUCCEEDED";
    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final RefundAttemptService refundAttemptService;
    private final RefundService refundService;
    private final CouponService couponService;
    private final CouponRedemptionService couponRedemptionService;
    private final CouponStatusHistoryService couponStatusHistoryService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final PortOnePaymentGateway portOnePaymentGateway;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public RecoverPendingRefundAttemptsUseCase(
        RefundAttemptService refundAttemptService,
        RefundService refundService,
        CouponService couponService,
        CouponRedemptionService couponRedemptionService,
        CouponStatusHistoryService couponStatusHistoryService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        PortOnePaymentGateway portOnePaymentGateway,
        Clock clock,
        PlatformTransactionManager transactionManager
    ) {
        this.refundAttemptService = refundAttemptService;
        this.refundService = refundService;
        this.couponService = couponService;
        this.couponRedemptionService = couponRedemptionService;
        this.couponStatusHistoryService = couponStatusHistoryService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.portOnePaymentGateway = portOnePaymentGateway;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public RecoveryResult recover() {
        List<RefundAttemptService.RecoveryCandidate> candidates = refundAttemptService.findRecoveryCandidates(
            Instant.now(clock).minus(RECOVERY_DELAY)
        );
        int recoveredCount = 0;
        int discrepantCount = 0;
        for (RefundAttemptService.RecoveryCandidate candidate : candidates) {
            try {
                PortOnePaymentGateway.PortOnePayment payment = portOnePaymentGateway.findByPaymentId(
                    candidate.portonePaymentId()
                );
                RecoveryOutcome outcome = executeInTransaction(() -> recoverFromPayment(
                    candidate,
                    payment,
                    UUID.randomUUID()
                ));
                recoveredCount += outcome.recoveredCount();
                discrepantCount += outcome.discrepantCount();
            } catch (PortOneLookupException | PortOneNoResponseException exception) {
                RecoveryOutcome outcome = executeInTransaction(() -> finalizeInterrupted(candidate, UUID.randomUUID()));
                recoveredCount += outcome.recoveredCount();
                discrepantCount += outcome.discrepantCount();
            }
        }
        return new RecoveryResult(candidates.size(), recoveredCount, discrepantCount);
    }

    private RecoveryOutcome recoverFromPayment(
        RefundAttemptService.RecoveryCandidate candidate,
        PortOnePaymentGateway.PortOnePayment payment,
        UUID requestId
    ) {
        Refund refund = refundService.findByRefundIdForUpdate(candidate.refundId()).orElse(null);
        RefundAttempt attempt = refundAttemptService
            .findByRefundAttemptIdForUpdate(candidate.refundAttemptId())
            .orElse(null);
        if (!isRecoverable(refund, attempt)) {
            return RecoveryOutcome.none();
        }
        PortOnePaymentGateway.PortOneCancellation cancellation = payment.cancellation();
        if (cancellation == null) {
            attempt.respond(null, payment.status(), payment.resultHash());
            Instant completedAt = Instant.now(clock);
            refund.fail(completedAt);
            recordRefundAudit(refund, requestId, completedAt);
            return RecoveryOutcome.recovered();
        }
        attempt.respond(cancellation.cancellationId(), cancellation.status(), cancellation.resultHash());
        if (cancellation.isSucceeded() && payment.isExplicitlyDeclined()) {
            Instant completedAt = Instant.now(clock);
            refund.succeed(completedAt);
            restoreCouponIfEligible(refund, requestId);
            recordRefundAudit(refund, requestId, completedAt);
            return RecoveryOutcome.recovered();
        }
        if (cancellation.isExplicitlyFailed()) {
            Instant completedAt = Instant.now(clock);
            refund.fail(completedAt);
            recordRefundAudit(refund, requestId, completedAt);
            return RecoveryOutcome.recovered();
        }
        Instant completedAt = Instant.now(clock);
        refund.markDiscrepant(completedAt);
        recordRefundAudit(refund, requestId, completedAt);
        return RecoveryOutcome.discrepant();
    }

    private RecoveryOutcome finalizeInterrupted(
        RefundAttemptService.RecoveryCandidate candidate,
        UUID requestId
    ) {
        Refund refund = refundService.findByRefundIdForUpdate(candidate.refundId()).orElse(null);
        RefundAttempt attempt = refundAttemptService
            .findByRefundAttemptIdForUpdate(candidate.refundAttemptId())
            .orElse(null);
        if (!isRecoverable(refund, attempt)) {
            return RecoveryOutcome.none();
        }
        attempt.noResponse(RefundFailureReasonCode.PROCESS_INTERRUPTED);
        Instant completedAt = Instant.now(clock);
        refund.markDiscrepant(completedAt);
        recordRefundAudit(refund, requestId, completedAt);
        return RecoveryOutcome.discrepant();
    }

    private boolean isRecoverable(Refund refund, RefundAttempt attempt) {
        return refund != null
            && refund.getStatus() == RefundStatus.PROCESSING
            && attempt != null
            && attempt.getOutcomeKind() == RefundAttemptOutcomeKind.PENDING;
    }

    private void restoreCouponIfEligible(
        Refund refund,
        UUID requestId
    ) {
        Reservation reservation = refund.getPayment().getReservation();
        if (reservation == null
            || reservation.getStatus() != ReservationStatus.CANCELLED
            || !reservation.getCancelledAt().isBefore(reservation.getContentSession().getStartsAt())
            || refund.getPayment().getReservationPriceSnapshot().getCoupon() == null) {
            return;
        }
        CouponRedemption redemption = couponRedemptionService.findByReservationPriceSnapshotIdForUpdate(
            refund.getPayment().getReservationPriceSnapshot().getReservationPriceSnapshotId()
        ).orElse(null);
        if (redemption == null || redemption.getStatus() == CouponRedemptionStatus.REVERSED) {
            return;
        }
        Coupon coupon = couponService.findByCouponIdForUpdate(redemption.getCoupon().getCouponId()).orElse(null);
        if (coupon == null || coupon.getStatus() != CouponStatus.USED) {
            return;
        }
        Instant restoredAt = couponService.findCurrentDatabaseTime();
        redemption.reverse(restoredAt);
        CouponStatus restoredStatus = couponService.restoreUsedCoupon(coupon, restoredAt);
        couponStatusHistoryService.create(new CouponStatusHistory(
            coupon,
            CouponStatus.USED,
            restoredStatus,
            COUPON_RESTORE_REASON,
            SYSTEM_ACTOR,
            restoredAt
        ));
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            reservation.getRegion(),
            AuditEventTargetType.COUPON,
            coupon.getCouponId(),
            CouponStatus.USED.name(),
            restoredStatus.name(),
            AuditEventResult.SUCCESS,
            COUPON_RESTORE_REASON,
            null,
            null,
            restoredAt
        ));
    }

    private void recordRefundAudit(
        Refund refund,
        UUID requestId,
        Instant completedAt
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            refund.getPayment().getCapacityHold().getRegion(),
            AuditEventTargetType.REFUND,
            refund.getRefundId(),
            RefundStatus.PROCESSING.name(),
            refund.getStatus().name(),
            AuditEventResult.SUCCESS,
            null,
            null,
            null,
            completedAt
        ));
    }

    private <T> T executeInTransaction(java.util.function.Supplier<T> action) {
        T result = transactionTemplate.execute(status -> action.get());
        if (result == null) {
            throw new IllegalStateException("transaction result must not be null");
        }
        return result;
    }

    public record RecoveryResult(
        int candidateCount,
        int recoveredCount,
        int discrepantCount
    ) {
    }

    private record RecoveryOutcome(int recoveredCount, int discrepantCount) {

        private static RecoveryOutcome none() {
            return new RecoveryOutcome(0, 0);
        }

        private static RecoveryOutcome recovered() {
            return new RecoveryOutcome(1, 0);
        }

        private static RecoveryOutcome discrepant() {
            return new RecoveryOutcome(1, 1);
        }
    }
}
