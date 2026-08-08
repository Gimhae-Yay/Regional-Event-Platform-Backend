package io.regionevent.regioneventbackend.domain.mission.entity;

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
    name = "mission_reward_claim",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_mission_reward_claim_participation",
        columnNames = "mission_participation_id"
    )
)
public class MissionRewardClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mission_reward_claim_id")
    private Long missionRewardClaimId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "mission_participation_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_mission_reward_claim_participation")
    )
    private MissionParticipation missionParticipation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "coupon_policy_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_mission_reward_claim_coupon_policy")
    )
    private CouponPolicy couponPolicy;

    @Column(name = "claimed_at", nullable = false, updatable = false)
    private Instant claimedAt;

    protected MissionRewardClaim() {
    }

    public MissionRewardClaim(
        MissionParticipation missionParticipation,
        CouponPolicy couponPolicy,
        Instant claimedAt
    ) {
        this.missionParticipation = requireNotNull(missionParticipation, "missionParticipation");
        this.couponPolicy = requireNotNull(couponPolicy, "couponPolicy");
        validateCompletedParticipation(missionParticipation);
        validateRewardCouponPolicy(missionParticipation, couponPolicy);
        this.claimedAt = requireNotNull(claimedAt, "claimedAt");
    }

    public Long getMissionRewardClaimId() {
        return missionRewardClaimId;
    }

    public MissionParticipation getMissionParticipation() {
        return missionParticipation;
    }

    public CouponPolicy getCouponPolicy() {
        return couponPolicy;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    private static void validateCompletedParticipation(MissionParticipation missionParticipation) {
        if (missionParticipation.getStatus() != MissionParticipationStatus.COMPLETED) {
            throw new IllegalArgumentException("missionParticipation must be completed");
        }
    }

    private static void validateRewardCouponPolicy(
        MissionParticipation missionParticipation,
        CouponPolicy couponPolicy
    ) {
        CouponPolicy rewardCouponPolicy = missionParticipation.getMission().getRewardCouponPolicy();
        if (!isSameCouponPolicy(rewardCouponPolicy, couponPolicy)) {
            throw new IllegalArgumentException("couponPolicy must match mission rewardCouponPolicy");
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
