package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuance;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionRewardClaim;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookRewardGrant;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;

class GetMyCouponResultTest {

    @Test
    void 방문_발급_이력의_근거_식별자를_반환한다() {
        Visit visit = mock(Visit.class);
        when(visit.getVisitId()).thenReturn(100L);

        assertThat(GetMyCouponResult.from(couponIssuance(visit, null, null)).sourceId()).isEqualTo(100L);
    }

    @Test
    void 미션_보상_발급_이력의_근거_식별자를_반환한다() {
        MissionRewardClaim missionRewardClaim = mock(MissionRewardClaim.class);
        when(missionRewardClaim.getMissionRewardClaimId()).thenReturn(200L);

        assertThat(GetMyCouponResult.from(couponIssuance(null, missionRewardClaim, null)).sourceId()).isEqualTo(200L);
    }

    @Test
    void 스탬프북_보상_발급_이력의_근거_식별자를_반환한다() {
        StampbookRewardGrant stampbookRewardGrant = mock(StampbookRewardGrant.class);
        when(stampbookRewardGrant.getStampbookRewardGrantId()).thenReturn(300L);

        assertThat(GetMyCouponResult.from(couponIssuance(null, null, stampbookRewardGrant)).sourceId()).isEqualTo(300L);
    }

    private CouponIssuance couponIssuance(
        Visit visit,
        MissionRewardClaim missionRewardClaim,
        StampbookRewardGrant stampbookRewardGrant
    ) {
        CouponIssuance couponIssuance = mock(CouponIssuance.class);
        Coupon coupon = mock(Coupon.class);
        CouponPolicy couponPolicy = mock(CouponPolicy.class);
        Content content = mock(Content.class);
        Region region = mock(Region.class);
        when(couponIssuance.getCoupon()).thenReturn(coupon);
        when(couponIssuance.getCouponPolicy()).thenReturn(couponPolicy);
        when(couponPolicy.getContent()).thenReturn(content);
        when(couponPolicy.getRegion()).thenReturn(region);
        when(couponIssuance.getVisit()).thenReturn(visit);
        when(couponIssuance.getMissionRewardClaim()).thenReturn(missionRewardClaim);
        when(couponIssuance.getStampbookRewardGrant()).thenReturn(stampbookRewardGrant);
        return couponIssuance;
    }
}
