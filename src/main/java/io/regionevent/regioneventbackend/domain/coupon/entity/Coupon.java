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

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Entity
@Table(
    name = "coupon",
    check = @CheckConstraint(
        name = "ck_coupon_status",
        constraint = "status REGEXP '^(AVAILABLE|RESERVED|USED|EXPIRED|INVALIDATED)$'"
    )
)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long couponId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "coupon_policy_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_coupon_policy")
    )
    private CouponPolicy couponPolicy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_id",
        foreignKey = @ForeignKey(name = "fk_coupon_user")
    )
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CouponStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected Coupon() {
    }

    public Coupon(
        CouponPolicy couponPolicy,
        AppUser user,
        Instant issuedAt,
        Instant expiresAt
    ) {
        this.couponPolicy = requireNotNull(couponPolicy, "couponPolicy");
        this.user = requireNotNull(user, "user");
        this.status = CouponStatus.AVAILABLE;
        this.issuedAt = requireNotNull(issuedAt, "issuedAt");
        this.expiresAt = requireNotNull(expiresAt, "expiresAt");
        validateExpiry(issuedAt, expiresAt);
    }

    public Long getCouponId() {
        return couponId;
    }

    public CouponPolicy getCouponPolicy() {
        return couponPolicy;
    }

    public AppUser getUser() {
        return user;
    }

    public CouponStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void reserve() {
        if (status != CouponStatus.AVAILABLE) {
            throw new IllegalStateException("only available coupon can be reserved");
        }
        status = CouponStatus.RESERVED;
    }

    public void use() {
        if (status != CouponStatus.RESERVED) {
            throw new IllegalStateException("only reserved coupon can be used");
        }
        status = CouponStatus.USED;
    }

    public CouponStatus release(Instant releasedAt) {
        if (status != CouponStatus.RESERVED) {
            throw new IllegalStateException("only reserved coupon can be released");
        }
        status = expiresAt.isAfter(requireNotNull(releasedAt, "releasedAt"))
            ? CouponStatus.AVAILABLE
            : CouponStatus.EXPIRED;
        return status;
    }

    private static void validateExpiry(
        Instant issuedAt,
        Instant expiresAt
    ) {
        if (!issuedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
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
