package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Instant;
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
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemption;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.service.PaymentService;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationPriceSnapshotService;

@Service
public class RestoreCouponUseCase {

    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final ReservationPriceSnapshotService reservationPriceSnapshotService;
    private final PaymentService paymentService;
    private final CouponRedemptionService couponRedemptionService;
    private final CouponService couponService;
    private final CouponStatusHistoryService couponStatusHistoryService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;

    public RestoreCouponUseCase(
        ReservationPriceSnapshotService reservationPriceSnapshotService,
        PaymentService paymentService,
        CouponRedemptionService couponRedemptionService,
        CouponService couponService,
        CouponStatusHistoryService couponStatusHistoryService,
        RecordAuditEventUseCase recordAuditEventUseCase
    ) {
        this.reservationPriceSnapshotService = reservationPriceSnapshotService;
        this.paymentService = paymentService;
        this.couponRedemptionService = couponRedemptionService;
        this.couponService = couponService;
        this.couponStatusHistoryService = couponStatusHistoryService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean restoreForRefund(
        Refund refund,
        UUID requestId,
        AuditEventActor actor
    ) {
        Reservation reservation = refund.getPayment().getReservation();
        ReservationPriceSnapshot snapshot = refund.getPayment().getReservationPriceSnapshot();
        if (!isEligibleCancellation(reservation) || snapshot.getCoupon() == null) {
            return false;
        }
        CouponRedemption redemption = findRedemption(snapshot);
        Coupon coupon = findCoupon(snapshot);
        validateRedemption(redemption, coupon, reservation, snapshot);

        Instant restoredAt = couponService.findCurrentDatabaseTime();
        if (!redemption.reverseForRefund(refund, restoredAt)) {
            return false;
        }
        restoreCoupon(redemption, coupon, reservation, requestId, actor, restoredAt);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean restoreForReservationCancellation(
        Reservation reservation,
        UUID requestId,
        AuditEventActor actor
    ) {
        if (!isEligibleCancellation(reservation)) {
            return false;
        }
        ReservationPriceSnapshot snapshot = reservationPriceSnapshotService
            .findByHoldIdForUpdate(reservation.getCapacityHold().getHoldId())
            .orElse(null);
        if (snapshot == null || snapshot.getFinalAmount() != 0 || snapshot.getCoupon() == null) {
            return false;
        }
        Payment payment = paymentService.findByReservationId(reservation.getReservationId()).orElse(null);
        if (payment != null) {
            throw new IllegalStateException("free reservation coupon reversal must not have payment");
        }
        CouponRedemption redemption = findRedemption(snapshot);
        Coupon coupon = findCoupon(snapshot);
        validateRedemption(redemption, coupon, reservation, snapshot);

        Instant restoredAt = couponService.findCurrentDatabaseTime();
        if (!redemption.reverseForReservationCancellation(restoredAt)) {
            return false;
        }
        restoreCoupon(redemption, coupon, reservation, requestId, actor, restoredAt);
        return true;
    }

    private boolean isEligibleCancellation(Reservation reservation) {
        return reservation != null
            && reservation.getStatus() == ReservationStatus.CANCELLED
            && reservation.getCancelledAt() != null
            && reservation.getCancelledAt().isBefore(reservation.getContentSession().getStartsAt());
    }

    private CouponRedemption findRedemption(ReservationPriceSnapshot snapshot) {
        return couponRedemptionService.findByReservationPriceSnapshotIdForUpdate(
            snapshot.getReservationPriceSnapshotId()
        ).orElseThrow(() -> new IllegalStateException("coupon redemption does not exist"));
    }

    private Coupon findCoupon(ReservationPriceSnapshot snapshot) {
        return couponService.findByCouponIdForUpdate(snapshot.getCoupon().getCouponId())
            .orElseThrow(() -> new IllegalStateException("snapshot coupon does not exist"));
    }

    private void validateRedemption(
        CouponRedemption redemption,
        Coupon coupon,
        Reservation reservation,
        ReservationPriceSnapshot snapshot
    ) {
        if (!redemption.getCoupon().getCouponId().equals(coupon.getCouponId())
            || !redemption.getReservation().getReservationId().equals(reservation.getReservationId())
            || !redemption.getReservationPriceSnapshot().getReservationPriceSnapshotId().equals(
                snapshot.getReservationPriceSnapshotId()
            )) {
            throw new IllegalStateException("coupon redemption does not match cancelled reservation");
        }
    }

    private void restoreCoupon(
        CouponRedemption redemption,
        Coupon coupon,
        Reservation reservation,
        UUID requestId,
        AuditEventActor actor,
        Instant restoredAt
    ) {
        if (coupon.getStatus() != CouponStatus.USED) {
            throw new IllegalStateException("coupon redemption coupon must be used");
        }
        CouponStatus restoredStatus = couponService.restoreUsedCoupon(coupon, restoredAt);
        couponStatusHistoryService.create(new CouponStatusHistory(
            coupon,
            CouponStatus.USED,
            restoredStatus,
            redemption.getReversalReasonCode().name(),
            actor == null ? SYSTEM_ACTOR : actor.getRoleName(),
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
            redemption.getReversalReasonCode().name(),
            actor,
            restoredAt
        ));
    }
}
