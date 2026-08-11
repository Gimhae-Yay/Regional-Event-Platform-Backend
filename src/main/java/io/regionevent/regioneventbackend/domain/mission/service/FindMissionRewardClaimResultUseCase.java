package io.regionevent.regioneventbackend.domain.mission.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.service.CouponIssuanceService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;

@Service
public class FindMissionRewardClaimResultUseCase {

    private final MissionRewardClaimService missionRewardClaimService;
    private final CouponIssuanceService couponIssuanceService;
    private final CouponStatusHistoryService couponStatusHistoryService;

    public FindMissionRewardClaimResultUseCase(
        MissionRewardClaimService missionRewardClaimService,
        CouponIssuanceService couponIssuanceService,
        CouponStatusHistoryService couponStatusHistoryService
    ) {
        this.missionRewardClaimService = missionRewardClaimService;
        this.couponIssuanceService = couponIssuanceService;
        this.couponStatusHistoryService = couponStatusHistoryService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ClaimMissionRewardResult> find(Long participationId) {
        return missionRewardClaimService.findByParticipationId(participationId)
            .flatMap(claim -> couponIssuanceService.findByMissionRewardClaimId(claim.getMissionRewardClaimId())
                .filter(issuance -> couponStatusHistoryService.findMissionRewardInitialByCouponId(
                    issuance.getCoupon().getCouponId()
                ).isPresent())
                .map(issuance -> ClaimMissionRewardResult.from(claim, issuance.getCoupon())));
    }
}
