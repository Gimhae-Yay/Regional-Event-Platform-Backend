package io.regionevent.regioneventbackend.domain.reservation.entity;

import java.time.Instant;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;

@Entity
@Table(
    name = "reservation_price_snapshot",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_reservation_price_snapshot_hold",
            columnNames = "hold_id"
        ),
        @UniqueConstraint(
            name = "uk_reservation_price_snapshot_id_coupon",
            columnNames = {"reservation_price_snapshot_id", "coupon_id"}
        )
    },
    check = @CheckConstraint(
        name = "ck_reservation_price_snapshot_amount",
        constraint = """
            base_amount >= 0
            AND discount_amount >= 0
            AND base_amount - discount_amount = final_amount
            AND final_amount >= 0
            """
    )
)
public class ReservationPriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_price_snapshot_id")
    private Long reservationPriceSnapshotId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "hold_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_reservation_price_snapshot_hold")
    )
    private CapacityHold capacityHold;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "coupon_id",
        foreignKey = @ForeignKey(name = "fk_reservation_price_snapshot_coupon")
    )
    private Coupon coupon;

    @Column(name = "base_amount", nullable = false, updatable = false)
    private long baseAmount;

    @Column(name = "discount_amount", nullable = false, updatable = false)
    private long discountAmount;

    @Column(name = "final_amount", nullable = false, updatable = false)
    private long finalAmount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReservationPriceSnapshot() {
    }

    public ReservationPriceSnapshot(
        CapacityHold capacityHold,
        Coupon coupon,
        long baseAmount,
        long discountAmount,
        long finalAmount,
        String currency,
        Instant createdAt
    ) {
        this.capacityHold = requireNotNull(capacityHold, "capacityHold");
        this.coupon = coupon;
        validateAmounts(baseAmount, discountAmount, finalAmount);
        this.baseAmount = baseAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.currency = requireCurrency(currency);
        this.createdAt = requireNotNull(createdAt, "createdAt");
    }

    public Long getReservationPriceSnapshotId() {
        return reservationPriceSnapshotId;
    }

    public CapacityHold getCapacityHold() {
        return capacityHold;
    }

    public Coupon getCoupon() {
        return coupon;
    }

    public long getBaseAmount() {
        return baseAmount;
    }

    public long getDiscountAmount() {
        return discountAmount;
    }

    public long getFinalAmount() {
        return finalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static void validateAmounts(
        long baseAmount,
        long discountAmount,
        long finalAmount
    ) {
        if (baseAmount < 0 || discountAmount < 0 || finalAmount < 0) {
            throw new IllegalArgumentException("amounts must not be negative");
        }
        if (baseAmount - discountAmount != finalAmount) {
            throw new IllegalArgumentException("finalAmount must equal baseAmount minus discountAmount");
        }
    }

    private static String requireCurrency(String value) {
        if (value == null || value.length() != 3) {
            throw new IllegalArgumentException("currency must be a three-letter code");
        }
        return value;
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
