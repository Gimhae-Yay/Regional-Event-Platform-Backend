package io.regionevent.regioneventbackend.domain.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;

@Service
public class ExpirePendingPaymentForTerminatedHoldUseCase {

    private static final String COUPON_RELEASED_REASON_PREFIX = "CAPACITY_HOLD_";

    private final PaymentService paymentService;
    private final PaymentIdempotencyService paymentIdempotencyService;
    private final CouponService couponService;
    private final CouponStatusHistoryService couponStatusHistoryService;

    public ExpirePendingPaymentForTerminatedHoldUseCase(
        PaymentService paymentService,
        PaymentIdempotencyService paymentIdempotencyService,
        CouponService couponService,
        CouponStatusHistoryService couponStatusHistoryService
    ) {
        this.paymentService = paymentService;
        this.paymentIdempotencyService = paymentIdempotencyService;
        this.couponService = couponService;
        this.couponStatusHistoryService = couponStatusHistoryService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void expire(CapacityHoldService.TerminatedCapacityHold capacityHold) {
        paymentService.expirePendingByHoldId(capacityHold.holdId(), capacityHold.occurredAt())
            .ifPresent(payment -> {
                paymentIdempotencyService.setPaymentResultExpiration(
                    payment,
                    payment.getFinalizedAt()
                );
                releaseSnapshotCoupon(payment.getReservationPriceSnapshot().getCoupon(), capacityHold);
            });
    }

    private void releaseSnapshotCoupon(
        Coupon snapshotCoupon,
        CapacityHoldService.TerminatedCapacityHold capacityHold
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
    }
}
