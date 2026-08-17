package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuance;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponIssuanceService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionRewardClaim;

class FindMissionRewardClaimResultUseCaseTest {

    private static final Long PARTICIPATION_ID = 701L;
    private static final Long CLAIM_ID = 9001L;
    private static final Long COUPON_ID = 8001L;

    private final MissionRewardClaimService claimService = mock(MissionRewardClaimService.class);
    private final CouponIssuanceService issuanceService = mock(CouponIssuanceService.class);
    private final CouponStatusHistoryService historyService = mock(CouponStatusHistoryService.class);
    private final FindMissionRewardClaimResultUseCase useCase = new FindMissionRewardClaimResultUseCase(
        claimService, issuanceService, historyService
    );

    @Test
    void find_수령_발급_최초상태이력이모두있으면완결결과를반환한다() {
        Fixture fixture = fixture();
        when(historyService.findMissionRewardInitialByCouponId(COUPON_ID))
            .thenReturn(Optional.of(mock(CouponStatusHistory.class)));

        assertThat(useCase.find(PARTICIPATION_ID))
            .contains(new ClaimMissionRewardResult(
                CLAIM_ID, PARTICIPATION_ID, COUPON_ID, 301L, fixture.claimedAt()
            ));
    }

    @Test
    void find_최초상태이력이없으면부분결과를반환하지않는다() {
        fixture();
        when(historyService.findMissionRewardInitialByCouponId(COUPON_ID)).thenReturn(Optional.empty());

        assertThat(useCase.find(PARTICIPATION_ID)).isEmpty();
    }

    private Fixture fixture() {
        Instant claimedAt = Instant.parse("2026-08-11T00:00:00Z");
        MissionParticipation participation = mock(MissionParticipation.class);
        CouponPolicy policy = mock(CouponPolicy.class);
        MissionRewardClaim claim = mock(MissionRewardClaim.class);
        Coupon coupon = mock(Coupon.class);
        CouponIssuance issuance = mock(CouponIssuance.class);
        when(participation.getMissionParticipationId()).thenReturn(PARTICIPATION_ID);
        when(policy.getCouponPolicyId()).thenReturn(301L);
        when(claim.getMissionRewardClaimId()).thenReturn(CLAIM_ID);
        when(claim.getMissionParticipation()).thenReturn(participation);
        when(claim.getCouponPolicy()).thenReturn(policy);
        when(claim.getClaimedAt()).thenReturn(claimedAt);
        when(coupon.getCouponId()).thenReturn(COUPON_ID);
        when(issuance.getCoupon()).thenReturn(coupon);
        when(claimService.findByParticipationId(PARTICIPATION_ID)).thenReturn(Optional.of(claim));
        when(issuanceService.findByMissionRewardClaimId(CLAIM_ID)).thenReturn(Optional.of(issuance));
        return new Fixture(claimedAt);
    }

    private record Fixture(Instant claimedAt) {
    }
}
