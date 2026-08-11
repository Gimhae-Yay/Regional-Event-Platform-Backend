package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
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
import io.regionevent.regioneventbackend.domain.payment.dto.CreateRefundRequest;
import io.regionevent.regioneventbackend.domain.payment.dto.CreateRefundResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptInitiatorKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundFailureReasonCode;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneNoResponseException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreateRefundUseCase {

    private static final String DISCREPANCY_ACTION_TYPE = "FULL_REFUND_REQUEST";
    private static final String DISCREPANCY_ACTION_REASON_CODE = "MANUAL_FULL_REFUND";
    private static final String DISCREPANCY_ACTION_RESULT_CODE = "REFUND_REQUESTED";
    private static final String COUPON_RESTORE_REASON = "REFUND_SUCCEEDED";

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final RefundAttemptService refundAttemptService;
    private final PaymentDiscrepancyService paymentDiscrepancyService;
    private final PaymentDiscrepancyActionService paymentDiscrepancyActionService;
    private final CouponService couponService;
    private final CouponRedemptionService couponRedemptionService;
    private final CouponStatusHistoryService couponStatusHistoryService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final PortOnePaymentGateway portOnePaymentGateway;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public CreateRefundUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        PaymentService paymentService,
        RefundService refundService,
        RefundAttemptService refundAttemptService,
        PaymentDiscrepancyService paymentDiscrepancyService,
        PaymentDiscrepancyActionService paymentDiscrepancyActionService,
        CouponService couponService,
        CouponRedemptionService couponRedemptionService,
        CouponStatusHistoryService couponStatusHistoryService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        PortOnePaymentGateway portOnePaymentGateway,
        Clock clock,
        PlatformTransactionManager transactionManager
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.refundAttemptService = refundAttemptService;
        this.paymentDiscrepancyService = paymentDiscrepancyService;
        this.paymentDiscrepancyActionService = paymentDiscrepancyActionService;
        this.couponService = couponService;
        this.couponRedemptionService = couponRedemptionService;
        this.couponStatusHistoryService = couponStatusHistoryService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.portOnePaymentGateway = portOnePaymentGateway;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public CreateRefundResponse create(
        Long actorUserId,
        String paymentIdValue,
        CreateRefundRequest request,
        UUID requestId
    ) {
        long paymentId = toPositiveId(paymentIdValue);
        String evidenceReference = normalizeRequired(request == null ? null : request.evidenceReference());
        String reason = normalizeRequired(request == null ? null : request.reason());

        PreparedRefund preparedRefund = executeInTransaction(
            () -> prepareRefund(
                actorUserId,
                paymentId,
                evidenceReference,
                reason
            )
        );
        if (preparedRefund.existingResponse() != null) {
            return preparedRefund.existingResponse();
        }

        try {
            PortOnePaymentGateway.PortOneCancellation cancellation = portOnePaymentGateway.cancelPayment(
                preparedRefund.portonePaymentId(),
                preparedRefund.amount(),
                reason
            );
            return executeInTransaction(() -> confirmResponse(
                actorUserId,
                preparedRefund,
                cancellation,
                evidenceReference,
                reason,
                requestId
            ));
        } catch (PortOneNoResponseException exception) {
            return executeInTransaction(() -> confirmNoResponse(
                actorUserId,
                preparedRefund,
                exception.getFailureReasonCode(),
                evidenceReference,
                reason,
                requestId
            ));
        }
    }

    private PreparedRefund prepareRefund(
        Long actorUserId,
        long paymentId,
        String evidenceReference,
        String reason
    ) {
        PlatformAdminAssignment assignment = platformAdminAuthorizationService
            .requireAuthorizedPlatformAdmin(actorUserId);
        Payment payment = paymentService.findByPaymentIdForUpdate(paymentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Refund existing = refundService.findByPaymentIdForUpdate(paymentId).orElse(null);
        if (existing != null) {
            return PreparedRefund.withExistingResponse(CreateRefundResponse.from(existing));
        }
        if (payment.getStatus() != PaymentStatus.APPROVED && payment.getStatus() != PaymentStatus.DISCREPANT) {
            throw new BusinessException(ErrorCode.REFUND_PAYMENT_CONFLICT);
        }
        if (payment.getPortonePaymentId() == null) {
            throw new BusinessException(ErrorCode.REFUND_PAYMENT_CONFLICT);
        }
        Instant now = Instant.now(clock);
        Refund refund = refundService.create(new Refund(
            payment,
            payment.getReservationPriceSnapshot().getFinalAmount(),
            now
        ));
        refund.startProcessing();
        RefundAttempt attempt = refundAttemptService.create(new RefundAttempt(
            refund,
            1,
            RefundAttemptInitiatorKind.SYSTEM,
            now
        ));
        requestDiscrepancyRefund(payment, evidenceReference, reason, now);
        return new PreparedRefund(
            refund.getRefundId(),
            attempt.getRefundAttemptId(),
            payment.getPortonePaymentId(),
            refund.getAmount(),
            null
        );
    }

    private CreateRefundResponse confirmResponse(
        Long actorUserId,
        PreparedRefund preparedRefund,
        PortOnePaymentGateway.PortOneCancellation cancellation,
        String evidenceReference,
        String reason,
        UUID requestId
    ) {
        PlatformAdminAssignment assignment = platformAdminAuthorizationService
            .requireAuthorizedPlatformAdmin(actorUserId);
        Refund refund = refundService.findByRefundIdForUpdate(preparedRefund.refundId())
            .orElseThrow(() -> new IllegalStateException("prepared refund does not exist"));
        RefundAttempt attempt = refundAttemptService
            .findByRefundAttemptIdForUpdate(preparedRefund.refundAttemptId())
            .orElseThrow(() -> new IllegalStateException("prepared refund attempt does not exist"));
        attempt.respond(cancellation.cancellationId(), cancellation.status(), cancellation.resultHash());
        if (cancellation.isSucceeded()) {
            Instant completedAt = Instant.now(clock);
            refund.succeed(completedAt);
            restoreCouponIfEligible(refund, completedAt, requestId, assignment);
        } else {
            refund.fail(Instant.now(clock));
        }
        recordRefundAudit(refund, assignment, evidenceReference, reason, requestId);
        return CreateRefundResponse.from(refund);
    }

    private CreateRefundResponse confirmNoResponse(
        Long actorUserId,
        PreparedRefund preparedRefund,
        RefundFailureReasonCode failureReasonCode,
        String evidenceReference,
        String reason,
        UUID requestId
    ) {
        PlatformAdminAssignment assignment = platformAdminAuthorizationService
            .requireAuthorizedPlatformAdmin(actorUserId);
        Refund refund = refundService.findByRefundIdForUpdate(preparedRefund.refundId())
            .orElseThrow(() -> new IllegalStateException("prepared refund does not exist"));
        RefundAttempt attempt = refundAttemptService
            .findByRefundAttemptIdForUpdate(preparedRefund.refundAttemptId())
            .orElseThrow(() -> new IllegalStateException("prepared refund attempt does not exist"));
        attempt.noResponse(failureReasonCode);
        refund.markDiscrepant(Instant.now(clock));
        recordRefundAudit(refund, assignment, evidenceReference, reason, requestId);
        return CreateRefundResponse.from(refund);
    }

    private void requestDiscrepancyRefund(
        Payment payment,
        String evidenceReference,
        String reason,
        Instant actedAt
    ) {
        PaymentDiscrepancy discrepancy = paymentDiscrepancyService
            .findByPaymentIdForUpdate(payment.getPaymentId())
            .orElse(null);
        if (discrepancy == null || !"OPEN".equals(discrepancy.getStatus())) {
            return;
        }
        discrepancy.requestRefund();
        paymentDiscrepancyActionService.create(
            discrepancy,
            DISCREPANCY_ACTION_TYPE,
            evidenceReference,
            DISCREPANCY_ACTION_REASON_CODE,
            DISCREPANCY_ACTION_RESULT_CODE,
            actedAt
        );
    }

    private void restoreCouponIfEligible(
        Refund refund,
        Instant restoredAt,
        UUID requestId,
        PlatformAdminAssignment assignment
    ) {
        Payment payment = refund.getPayment();
        Reservation reservation = payment.getReservation();
        if (reservation == null
            || reservation.getStatus() != ReservationStatus.CANCELLED
            || !reservation.getCancelledAt().isBefore(reservation.getContentSession().getStartsAt())
            || payment.getReservationPriceSnapshot().getCoupon() == null) {
            return;
        }
        CouponRedemption redemption = couponRedemptionService
            .findByReservationPriceSnapshotIdForUpdate(
                payment.getReservationPriceSnapshot().getReservationPriceSnapshotId()
            )
            .orElse(null);
        if (redemption == null || redemption.getStatus() == CouponRedemptionStatus.REVERSED) {
            return;
        }
        Coupon snapshotCoupon = payment.getReservationPriceSnapshot().getCoupon();
        Coupon coupon = couponService.findByCouponIdForUpdate(snapshotCoupon.getCouponId())
            .orElseThrow(() -> new IllegalStateException("snapshot coupon does not exist"));
        if (redemption.getCoupon().getCouponId().equals(coupon.getCouponId())
            && redemption.getReservation().getReservationId().equals(reservation.getReservationId())
            && redemption.getReservationPriceSnapshot().getReservationPriceSnapshotId().equals(
                payment.getReservationPriceSnapshot().getReservationPriceSnapshotId()
            )
            && coupon.getStatus() == CouponStatus.USED) {
            redemption.reverse(restoredAt);
            CouponStatus restoredStatus = couponService.restoreUsedCoupon(coupon, restoredAt);
            couponStatusHistoryService.create(new CouponStatusHistory(
                coupon,
                CouponStatus.USED,
                restoredStatus,
                COUPON_RESTORE_REASON,
                assignment.getGrade().name(),
                restoredAt
            ));
            recordAuditEventUseCase.record(new AuditEventCommand(
                requestId,
                payment.getCapacityHold().getRegion(),
                AuditEventTargetType.COUPON,
                coupon.getCouponId(),
                CouponStatus.USED.name(),
                restoredStatus.name(),
                AuditEventResult.SUCCESS,
                COUPON_RESTORE_REASON,
                null,
                null,
                new AuditEventActor(assignment),
                restoredAt
            ));
        }
    }

    private long toPositiveId(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!value.matches("[0-9]+")) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        try {
            long id = Long.parseLong(value);
            if (id < 1) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private void recordRefundAudit(
        Refund refund,
        PlatformAdminAssignment assignment,
        String evidenceReference,
        String reason,
        UUID requestId
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            refund.getPayment().getCapacityHold().getRegion(),
            AuditEventTargetType.REFUND,
            refund.getRefundId(),
            "REQUESTED",
            refund.getStatus().name(),
            AuditEventResult.SUCCESS,
            null,
            reason,
            evidenceReference,
            new AuditEventActor(assignment),
            Instant.now(clock)
        ));
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        T result = transactionTemplate.execute(status -> action.get());
        if (result == null) {
            throw new IllegalStateException("transaction result must not be null");
        }
        return result;
    }

    private record PreparedRefund(
        Long refundId,
        Long refundAttemptId,
        String portonePaymentId,
        long amount,
        CreateRefundResponse existingResponse
    ) {

        private static PreparedRefund withExistingResponse(CreateRefundResponse response) {
            return new PreparedRefund(null, null, null, 0, response);
        }
    }
}
