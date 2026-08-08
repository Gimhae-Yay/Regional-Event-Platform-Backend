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
import jakarta.persistence.Table;

@Entity
@Table(
    name = "coupon_status_history",
    check = {
        @CheckConstraint(
            name = "ck_coupon_status_history_previous_status",
            constraint = "previous_status IS NULL OR previous_status REGEXP '^(AVAILABLE|RESERVED|USED|EXPIRED|INVALIDATED)$'"
        ),
        @CheckConstraint(
            name = "ck_coupon_status_history_next_status",
            constraint = "next_status REGEXP '^(AVAILABLE|RESERVED|USED|EXPIRED|INVALIDATED)$'"
        )
    }
)
public class CouponStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_status_history_id")
    private Long couponStatusHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "coupon_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_coupon_status_history_coupon")
    )
    private Coupon coupon;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30, updatable = false)
    private CouponStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_status", nullable = false, length = 30, updatable = false)
    private CouponStatus nextStatus;

    @Column(name = "reason_code", nullable = false, length = 100, updatable = false)
    private String reasonCode;

    @Column(name = "actor_kind", nullable = false, length = 30, updatable = false)
    private String actorKind;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected CouponStatusHistory() {
    }

    public CouponStatusHistory(
        Coupon coupon,
        CouponStatus previousStatus,
        CouponStatus nextStatus,
        String reasonCode,
        String actorKind,
        Instant occurredAt
    ) {
        this.coupon = requireNotNull(coupon, "coupon");
        this.previousStatus = previousStatus;
        this.nextStatus = requireNotNull(nextStatus, "nextStatus");
        this.reasonCode = requireNotBlank(reasonCode, "reasonCode");
        this.actorKind = requireNotBlank(actorKind, "actorKind");
        this.occurredAt = requireNotNull(occurredAt, "occurredAt");
    }

    public Long getCouponStatusHistoryId() {
        return couponStatusHistoryId;
    }

    public Coupon getCoupon() {
        return coupon;
    }

    public CouponStatus getPreviousStatus() {
        return previousStatus;
    }

    public CouponStatus getNextStatus() {
        return nextStatus;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getActorKind() {
        return actorKind;
    }

    public Instant getOccurredAt() {
        return occurredAt;
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

    private static String requireNotBlank(
        String value,
        String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
