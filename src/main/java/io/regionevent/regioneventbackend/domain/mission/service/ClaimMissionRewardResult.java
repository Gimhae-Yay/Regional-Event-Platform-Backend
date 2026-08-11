package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionRewardClaim;

public record ClaimMissionRewardResult(
    Long missionRewardClaimId,
    Long participationId,
    Long couponId,
    Long couponPolicyId,
    Instant claimedAt
) {

    static ClaimMissionRewardResult from(MissionRewardClaim claim, Coupon coupon) {
        return new ClaimMissionRewardResult(
            claim.getMissionRewardClaimId(),
            claim.getMissionParticipation().getMissionParticipationId(),
            coupon.getCouponId(),
            claim.getCouponPolicy().getCouponPolicyId(),
            claim.getClaimedAt()
        );
    }
}
