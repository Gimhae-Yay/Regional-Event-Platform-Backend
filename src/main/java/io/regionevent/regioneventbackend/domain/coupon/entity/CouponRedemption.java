package io.regionevent.regioneventbackend.domain.coupon.entity;

import java.time.Instant;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;

@Entity
@Table(
    name = "coupon_redemption",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_coupon_redemption_reservation",
            columnNames = "reservation_id"
        ),
        @UniqueConstraint(
            name = "uk_coupon_redemption_snapshot",
            columnNames = "reservation_price_snapshot_id"
        ),
        @UniqueConstraint(
            name = "uk_coupon_redemption_refund",
            columnNames = "refund_id"
        )
    },
    check = {
        @CheckConstraint(
            name = "ck_coupon_redemption_status",
            constraint = "status REGEXP '^(CONFIRMED|REVERSED)$'"
        ),
        @CheckConstraint(
            name = "ck_coupon_redemption_reversal",
            constraint = """
                (status = 'CONFIRMED'
                    AND refund_id IS NULL
                    AND reversal_reason_code IS NULL
                    AND reversed_at IS NULL)
                OR (status = 'REVERSED'
                    AND refund_id IS NOT NULL
                    AND reversal_reason_code = 'REFUND_SUCCEEDED'
                    AND reversed_at IS NOT NULL)
                OR (status = 'REVERSED'
                    AND refund_id IS NULL
                    AND reversal_reason_code = 'RESERVATION_CANCELLED'
                    AND reversed_at IS NOT NULL)
                """
        )
    }
)
public class CouponRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_redemption_id")
    private Long couponRedemptionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "coupon_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_coupon_redemption_coupon")
    )
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "reservation_price_snapshot_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_coupon_redemption_snapshot_coupon")
    )
    private ReservationPriceSnapshot reservationPriceSnapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "reservation_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_coupon_redemption_reservation")
    )
    private Reservation reservation;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "refund_id",
        unique = true,
        foreignKey = @ForeignKey(name = "fk_coupon_redemption_refund")
    )
    private Refund refund;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CouponRedemptionStatus status;

    @Column(name = "redeemed_at", nullable = false, updatable = false)
    private Instant redeemedAt;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reversal_reason_code", length = 50)
    private CouponRedemptionReversalReason reversalReasonCode;

    protected CouponRedemption() {
    }

    public CouponRedemption(
        Coupon coupon,
        ReservationPriceSnapshot reservationPriceSnapshot,
        Reservation reservation,
        Instant redeemedAt
    ) {
        this.coupon = requireNotNull(coupon, "coupon");
        this.reservationPriceSnapshot = requireNotNull(
            reservationPriceSnapshot,
            "reservationPriceSnapshot"
        );
        validateSnapshotCoupon(reservationPriceSnapshot, coupon);
        this.reservation = requireNotNull(reservation, "reservation");
        validateReservationHold(reservationPriceSnapshot, reservation);
        this.status = CouponRedemptionStatus.CONFIRMED;
        this.redeemedAt = requireNotNull(redeemedAt, "redeemedAt");
    }

    public Long getCouponRedemptionId() {
        return couponRedemptionId;
    }

    public Coupon getCoupon() {
        return coupon;
    }

    public ReservationPriceSnapshot getReservationPriceSnapshot() {
        return reservationPriceSnapshot;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public CouponRedemptionStatus getStatus() {
        return status;
    }

    public Refund getRefund() {
        return refund;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public CouponRedemptionReversalReason getReversalReasonCode() {
        return reversalReasonCode;
    }

    public boolean reverseForRefund(
        Refund refund,
        Instant reversedAt
    ) {
        Refund reversalRefund = requireNotNull(refund, "refund");
        Instant reversalTime = requireNotNull(reversedAt, "reversedAt");
        if (reversalRefund.getRefundId() == null) {
            throw new IllegalArgumentException("refund must be persisted");
        }
        if (reversalRefund.getStatus() != RefundStatus.SUCCEEDED) {
            throw new IllegalStateException("refund must be succeeded");
        }
        validateRefundSource(reversalRefund);
        if (!prepareReversal(reversalRefund, CouponRedemptionReversalReason.REFUND_SUCCEEDED)) {
            return false;
        }
        this.refund = reversalRefund;
        reversalReasonCode = CouponRedemptionReversalReason.REFUND_SUCCEEDED;
        completeReversal(reversalTime);
        return true;
    }

    public boolean reverseForReservationCancellation(Instant reversedAt) {
        Instant reversalTime = requireNotNull(reversedAt, "reversedAt");
        if (!prepareReversal(null, CouponRedemptionReversalReason.RESERVATION_CANCELLED)) {
            return false;
        }
        reversalReasonCode = CouponRedemptionReversalReason.RESERVATION_CANCELLED;
        completeReversal(reversalTime);
        return true;
    }

    private boolean prepareReversal(
        Refund requestedRefund,
        CouponRedemptionReversalReason requestedReason
    ) {
        if (status == CouponRedemptionStatus.CONFIRMED) {
            return true;
        }
        if (status == CouponRedemptionStatus.REVERSED
            && hasSameRefund(requestedRefund)
            && reversalReasonCode == requestedReason) {
            return false;
        }
        throw new IllegalStateException("coupon redemption was reversed by a different source");
    }

    private boolean hasSameRefund(Refund requestedRefund) {
        if (refund == null || requestedRefund == null) {
            return refund == requestedRefund;
        }
        return refund == requestedRefund || refund.getRefundId().equals(requestedRefund.getRefundId());
    }

    private void completeReversal(Instant reversedAt) {
        status = CouponRedemptionStatus.REVERSED;
        this.reversedAt = reversedAt;
    }

    private void validateRefundSource(Refund refund) {
        if (refund.getPayment().getReservation() == null
            || !refund.getPayment().getReservation().getReservationId().equals(reservation.getReservationId())
            || !refund.getPayment().getReservationPriceSnapshot().getReservationPriceSnapshotId().equals(
                reservationPriceSnapshot.getReservationPriceSnapshotId()
            )) {
            throw new IllegalArgumentException("refund must belong to coupon redemption reservation");
        }
    }

    private static void validateSnapshotCoupon(
        ReservationPriceSnapshot reservationPriceSnapshot,
        Coupon coupon
    ) {
        Coupon snapshotCoupon = reservationPriceSnapshot.getCoupon();
        if (snapshotCoupon != coupon) {
            Long snapshotCouponId = snapshotCoupon == null ? null : snapshotCoupon.getCouponId();
            Long couponId = coupon.getCouponId();
            if (snapshotCouponId == null || !snapshotCouponId.equals(couponId)) {
                throw new IllegalArgumentException("coupon must match reservationPriceSnapshot coupon");
            }
        }
    }

    private static void validateReservationHold(
        ReservationPriceSnapshot reservationPriceSnapshot,
        Reservation reservation
    ) {
        CapacityHold snapshotHold = reservationPriceSnapshot.getCapacityHold();
        CapacityHold reservationHold = reservation.getCapacityHold();
        if (snapshotHold != reservationHold) {
            Long snapshotHoldId = snapshotHold.getHoldId();
            Long reservationHoldId = reservationHold.getHoldId();
            if (snapshotHoldId == null || !snapshotHoldId.equals(reservationHoldId)) {
                throw new IllegalArgumentException(
                    "reservation must belong to reservationPriceSnapshot capacityHold"
                );
            }
        }
    }

    private static <T> T requireNotNull(
        T value,
        String fieldName
    ) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
