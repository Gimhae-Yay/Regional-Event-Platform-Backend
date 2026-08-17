package io.regionevent.regioneventbackend.domain.payment.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;

@Service
public class ExpirePendingPaymentForTerminatedHoldUseCase {

    private static final String COUPON_RELEASED_REASON_PREFIX = "CAPACITY_HOLD_";

    private final PaymentService paymentService;
    private final PaymentIdempotencyService paymentIdempotencyService;
    private final CouponService couponService;
    private final CouponStatusHistoryService couponStatusHistoryService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;

    public ExpirePendingPaymentForTerminatedHoldUseCase(
        PaymentService paymentService,
        PaymentIdempotencyService paymentIdempotencyService,
        CouponService couponService,
        CouponStatusHistoryService couponStatusHistoryService,
        RecordAuditEventUseCase recordAuditEventUseCase
    ) {
        this.paymentService = paymentService;
        this.paymentIdempotencyService = paymentIdempotencyService;
        this.couponService = couponService;
        this.couponStatusHistoryService = couponStatusHistoryService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean expire(
        CapacityHoldService.TerminatedCapacityHold capacityHold,
        UUID requestId,
        AuditEventActor actor
    ) {
        recordCapacityHoldAuditEvent(capacityHold, requestId, actor);
        return paymentService.expirePendingByHoldId(capacityHold.holdId(), capacityHold.occurredAt())
            .map(payment -> {
                paymentIdempotencyService.setPaymentResultExpiration(
                    payment,
                    payment.getFinalizedAt()
                );
                recordPaymentAuditEvent(payment, capacityHold, requestId, actor);
                releaseSnapshotCoupon(
                    payment.getReservationPriceSnapshot().getCoupon(),
                    capacityHold,
                    requestId,
                    actor
                );
                return true;
            })
            .orElse(false);
    }

    private void releaseSnapshotCoupon(
        Coupon snapshotCoupon,
        CapacityHoldService.TerminatedCapacityHold capacityHold,
        UUID requestId,
        AuditEventActor actor
    ) {
        if (snapshotCoupon == null) {
            return;
        }
        Coupon coupon = couponService.findByCouponIdForUpdate(snapshotCoupon.getCouponId())
            .orElseThrow(() -> new IllegalStateException("snapshot coupon does not exist"));
        CouponStatus previousStatus = coupon.getStatus();
        CouponStatus nextStatus = coupon.release(capacityHold.occurredAt());
        couponStatusHistoryService.create(new CouponStatusHistory(
            coupon,
            previousStatus,
            nextStatus,
            COUPON_RELEASED_REASON_PREFIX + capacityHold.nextStatus().name(),
            "SYSTEM",
            capacityHold.occurredAt()
        ));
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            coupon.getCouponPolicy().getRegion(),
            AuditEventTargetType.COUPON,
            coupon.getCouponId(),
            previousStatus.name(),
            nextStatus.name(),
            AuditEventResult.SUCCESS,
            COUPON_RELEASED_REASON_PREFIX + capacityHold.nextStatus().name(),
            actor,
            capacityHold.occurredAt()
        ));
    }

    private void recordCapacityHoldAuditEvent(
        CapacityHoldService.TerminatedCapacityHold capacityHold,
        UUID requestId,
        AuditEventActor actor
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            capacityHold.region(),
            AuditEventTargetType.CAPACITY_HOLD,
            capacityHold.holdId(),
            CapacityHoldStatus.ACTIVE.name(),
            capacityHold.nextStatus().name(),
            AuditEventResult.SUCCESS,
            auditReasonCode(capacityHold.reasonCode()),
            auditReason(capacityHold.reasonCode()),
            null,
            actor,
            capacityHold.occurredAt()
        ));
    }

    private void recordPaymentAuditEvent(
        Payment payment,
        CapacityHoldService.TerminatedCapacityHold capacityHold,
        UUID requestId,
        AuditEventActor actor
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            payment.getCapacityHold().getRegion(),
            AuditEventTargetType.PAYMENT,
            payment.getPaymentId(),
            "PENDING",
            "EXPIRED",
            AuditEventResult.SUCCESS,
            auditReasonCode(capacityHold.reasonCode()),
            auditReason(capacityHold.reasonCode()),
            null,
            actor,
            payment.getFinalizedAt()
        ));
    }

    private String auditReasonCode(String value) {
        return value != null && value.matches("^[A-Z][A-Z0-9_]*$") ? value : null;
    }

    private String auditReason(String value) {
        return auditReasonCode(value) == null ? value : null;
    }
}
