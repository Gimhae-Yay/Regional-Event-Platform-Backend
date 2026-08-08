package io.regionevent.regioneventbackend.domain.coupon.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionRewardClaim;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgress;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookRewardGrant;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;

class CouponIssuanceTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void 방문_근거의_사용자_콘텐츠_지역이_발급_정보와_일치해야_한다() {
        IssuanceFixtures fixtures = createFixtures(CouponIssuanceType.VISIT);

        assertInvalidVisitSource(
            fixtures,
            visit(fixtures.otherUser(), fixtures.content(), fixtures.region())
        );
        assertInvalidVisitSource(
            fixtures,
            visit(fixtures.recipientUser(), fixtures.otherContent(), fixtures.region())
        );
        assertInvalidVisitSource(
            fixtures,
            visit(fixtures.recipientUser(), fixtures.content(), fixtures.otherRegion())
        );
    }

    @Test
    void 미션_보상_근거의_정책_사용자_지역이_발급_정보와_일치해야_한다() {
        IssuanceFixtures fixtures = createFixtures(CouponIssuanceType.MISSION_REWARD);

        assertInvalidMissionRewardSource(
            fixtures,
            missionRewardClaim(fixtures.otherCouponPolicy(), fixtures.recipientUser(), fixtures.region())
        );
        assertInvalidMissionRewardSource(
            fixtures,
            missionRewardClaim(fixtures.couponPolicy(), fixtures.otherUser(), fixtures.region())
        );
        assertInvalidMissionRewardSource(
            fixtures,
            missionRewardClaim(fixtures.couponPolicy(), fixtures.recipientUser(), fixtures.otherRegion())
        );
    }

    @Test
    void 스탬프북_보상_근거의_정책_사용자_지역이_발급_정보와_일치해야_한다() {
        IssuanceFixtures fixtures = createFixtures(CouponIssuanceType.STAMPBOOK_COMPLETION);

        assertInvalidStampbookRewardSource(
            fixtures,
            stampbookRewardGrant(fixtures.otherCouponPolicy(), fixtures.recipientUser(), fixtures.region())
        );
        assertInvalidStampbookRewardSource(
            fixtures,
            stampbookRewardGrant(fixtures.couponPolicy(), fixtures.otherUser(), fixtures.region())
        );
        assertInvalidStampbookRewardSource(
            fixtures,
            stampbookRewardGrant(fixtures.couponPolicy(), fixtures.recipientUser(), fixtures.otherRegion())
        );
    }

    private void assertInvalidVisitSource(
        IssuanceFixtures fixtures,
        Visit visit
    ) {
        assertThatThrownBy(() -> new CouponIssuance(
            fixtures.coupon(),
            fixtures.couponPolicy(),
            fixtures.recipientUser(),
            visit,
            null,
            null,
            "issuance-hash",
            ISSUED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private void assertInvalidMissionRewardSource(
        IssuanceFixtures fixtures,
        MissionRewardClaim missionRewardClaim
    ) {
        assertThatThrownBy(() -> new CouponIssuance(
            fixtures.coupon(),
            fixtures.couponPolicy(),
            fixtures.recipientUser(),
            null,
            missionRewardClaim,
            null,
            "issuance-hash",
            ISSUED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private void assertInvalidStampbookRewardSource(
        IssuanceFixtures fixtures,
        StampbookRewardGrant stampbookRewardGrant
    ) {
        assertThatThrownBy(() -> new CouponIssuance(
            fixtures.coupon(),
            fixtures.couponPolicy(),
            fixtures.recipientUser(),
            null,
            null,
            stampbookRewardGrant,
            "issuance-hash",
            ISSUED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static IssuanceFixtures createFixtures(CouponIssuanceType issuanceType) {
        AppUser recipientUser = mock(AppUser.class);
        AppUser otherUser = mock(AppUser.class);
        Content content = mock(Content.class);
        Content otherContent = mock(Content.class);
        Region region = mock(Region.class);
        Region otherRegion = mock(Region.class);
        CouponPolicy couponPolicy = mock(CouponPolicy.class);
        CouponPolicy otherCouponPolicy = mock(CouponPolicy.class);
        Coupon coupon = mock(Coupon.class);
        when(recipientUser.getUserId()).thenReturn(1L);
        when(otherUser.getUserId()).thenReturn(2L);
        when(content.getContentId()).thenReturn(1L);
        when(otherContent.getContentId()).thenReturn(2L);
        when(region.getRegionId()).thenReturn(1L);
        when(otherRegion.getRegionId()).thenReturn(2L);
        when(couponPolicy.getCouponPolicyId()).thenReturn(1L);
        when(otherCouponPolicy.getCouponPolicyId()).thenReturn(2L);
        when(couponPolicy.getIssuanceType()).thenReturn(issuanceType);
        when(couponPolicy.getContent()).thenReturn(content);
        when(couponPolicy.getRegion()).thenReturn(region);
        when(coupon.getCouponPolicy()).thenReturn(couponPolicy);
        when(coupon.getUser()).thenReturn(recipientUser);

        return new IssuanceFixtures(
            coupon,
            couponPolicy,
            otherCouponPolicy,
            recipientUser,
            otherUser,
            content,
            otherContent,
            region,
            otherRegion
        );
    }

    private static Visit visit(
        AppUser user,
        Content content,
        Region region
    ) {
        Visit visit = mock(Visit.class);
        when(visit.getUser()).thenReturn(user);
        when(visit.getContent()).thenReturn(content);
        when(visit.getRegion()).thenReturn(region);
        return visit;
    }

    private static MissionRewardClaim missionRewardClaim(
        CouponPolicy couponPolicy,
        AppUser user,
        Region region
    ) {
        Mission mission = mock(Mission.class);
        MissionParticipation missionParticipation = mock(MissionParticipation.class);
        MissionRewardClaim missionRewardClaim = mock(MissionRewardClaim.class);
        when(mission.getRegion()).thenReturn(region);
        when(missionParticipation.getMission()).thenReturn(mission);
        when(missionParticipation.getUser()).thenReturn(user);
        when(missionRewardClaim.getCouponPolicy()).thenReturn(couponPolicy);
        when(missionRewardClaim.getMissionParticipation()).thenReturn(missionParticipation);
        return missionRewardClaim;
    }

    private static StampbookRewardGrant stampbookRewardGrant(
        CouponPolicy couponPolicy,
        AppUser user,
        Region region
    ) {
        Stampbook stampbook = mock(Stampbook.class);
        StampbookProgress stampbookProgress = mock(StampbookProgress.class);
        StampbookRewardGrant stampbookRewardGrant = mock(StampbookRewardGrant.class);
        when(stampbook.getRegion()).thenReturn(region);
        when(stampbookProgress.getStampbook()).thenReturn(stampbook);
        when(stampbookProgress.getUser()).thenReturn(user);
        when(stampbookRewardGrant.getCouponPolicy()).thenReturn(couponPolicy);
        when(stampbookRewardGrant.getStampbookProgress()).thenReturn(stampbookProgress);
        return stampbookRewardGrant;
    }

    private record IssuanceFixtures(
        Coupon coupon,
        CouponPolicy couponPolicy,
        CouponPolicy otherCouponPolicy,
        AppUser recipientUser,
        AppUser otherUser,
        Content content,
        Content otherContent,
        Region region,
        Region otherRegion
    ) {
    }
}
