package io.regionevent.regioneventbackend.domain.mission.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.mission.service.ClaimMissionRewardResult;

public record ClaimMissionRewardResponse(
    String missionRewardClaimId,
    String participationId,
    String couponId,
    String couponPolicyId,
    Instant claimedAt
) {

    public static ClaimMissionRewardResponse from(ClaimMissionRewardResult result) {
        return new ClaimMissionRewardResponse(
            result.missionRewardClaimId().toString(),
            result.participationId().toString(),
            result.couponId().toString(),
            result.couponPolicyId().toString(),
            result.claimedAt()
        );
    }
}
