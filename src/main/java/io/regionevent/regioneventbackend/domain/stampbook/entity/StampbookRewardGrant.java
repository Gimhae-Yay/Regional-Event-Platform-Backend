package io.regionevent.regioneventbackend.domain.stampbook.entity;

import java.time.Instant;

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

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;

@Entity
@Table(
    name = "stampbook_reward_grant",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_stampbook_reward_grant_progress",
        columnNames = "stampbook_progress_id"
    )
)
public class StampbookRewardGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stampbook_reward_grant_id")
    private Long stampbookRewardGrantId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "stampbook_progress_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_stampbook_reward_grant_progress")
    )
    private StampbookProgress stampbookProgress;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "coupon_policy_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_stampbook_reward_grant_coupon_policy")
    )
    private CouponPolicy couponPolicy;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    protected StampbookRewardGrant() {
    }

    public StampbookRewardGrant(
        StampbookProgress stampbookProgress,
        CouponPolicy couponPolicy,
        Instant grantedAt
    ) {
        this.stampbookProgress = requireNotNull(stampbookProgress, "stampbookProgress");
        this.couponPolicy = requireNotNull(couponPolicy, "couponPolicy");
        validateCompletedProgress(stampbookProgress);
        validateRewardCouponPolicy(stampbookProgress, couponPolicy);
        this.grantedAt = requireNotNull(grantedAt, "grantedAt");
    }

    public Long getStampbookRewardGrantId() {
        return stampbookRewardGrantId;
    }

    public StampbookProgress getStampbookProgress() {
        return stampbookProgress;
    }

    public CouponPolicy getCouponPolicy() {
        return couponPolicy;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    private static void validateCompletedProgress(StampbookProgress stampbookProgress) {
        if (stampbookProgress.getStatus() != StampbookProgressStatus.COMPLETED) {
            throw new IllegalArgumentException("stampbookProgress must be completed");
        }
    }

    private static void validateRewardCouponPolicy(
        StampbookProgress stampbookProgress,
        CouponPolicy couponPolicy
    ) {
        CouponPolicy rewardCouponPolicy = stampbookProgress.getStampbook().getRewardCouponPolicy();
        if (!isSameCouponPolicy(rewardCouponPolicy, couponPolicy)) {
            throw new IllegalArgumentException("couponPolicy must match stampbook rewardCouponPolicy");
        }
    }

    private static boolean isSameCouponPolicy(
        CouponPolicy firstCouponPolicy,
        CouponPolicy secondCouponPolicy
    ) {
        if (firstCouponPolicy == secondCouponPolicy) {
            return true;
        }
        Long firstCouponPolicyId = firstCouponPolicy.getCouponPolicyId();
        Long secondCouponPolicyId = secondCouponPolicy.getCouponPolicyId();
        return firstCouponPolicyId != null
            && firstCouponPolicyId.equals(secondCouponPolicyId);
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
