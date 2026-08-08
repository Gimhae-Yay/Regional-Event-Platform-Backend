package io.regionevent.regioneventbackend.domain.stampbook.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;

class StampbookRewardGrantTest {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant GRANTED_AT = Instant.parse("2026-08-10T00:01:00Z");

    @Test
    void 완료된_진행에_스탬프북_보상_정책으로_지급_근거를_생성한다() {
        CouponPolicy rewardCouponPolicy = createRewardCouponPolicy();
        StampbookProgress completedProgress = createCompletedProgress(rewardCouponPolicy);

        StampbookRewardGrant rewardGrant = new StampbookRewardGrant(
            completedProgress,
            rewardCouponPolicy,
            GRANTED_AT
        );

        assertThat(rewardGrant.getStampbookProgress()).isSameAs(completedProgress);
        assertThat(rewardGrant.getCouponPolicy()).isSameAs(rewardCouponPolicy);
        assertThat(rewardGrant.getGrantedAt()).isEqualTo(GRANTED_AT);
    }

    @Test
    void 완료되지_않은_진행에는_지급_근거를_생성할_수_없다() {
        CouponPolicy rewardCouponPolicy = createRewardCouponPolicy();
        StampbookProgress inProgress = new StampbookProgress(
            new Stampbook(createRegion(), rewardCouponPolicy),
            createUser()
        );

        assertThatThrownBy(() -> new StampbookRewardGrant(
            inProgress,
            rewardCouponPolicy,
            GRANTED_AT
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("stampbookProgress must be completed");
    }

    @Test
    void 스탬프북의_완료_보상_정책과_다른_정책으로_지급_근거를_생성할_수_없다() {
        CouponPolicy rewardCouponPolicy = createRewardCouponPolicy();
        StampbookProgress completedProgress = createCompletedProgress(rewardCouponPolicy);

        assertThatThrownBy(() -> new StampbookRewardGrant(
            completedProgress,
            createRewardCouponPolicy(),
            GRANTED_AT
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("couponPolicy must match stampbook rewardCouponPolicy");
    }

    private StampbookProgress createCompletedProgress(CouponPolicy rewardCouponPolicy) {
        StampbookProgress stampbookProgress = new StampbookProgress(
            new Stampbook(createRegion(), rewardCouponPolicy),
            createUser()
        );
        stampbookProgress.complete(COMPLETED_AT);
        return stampbookProgress;
    }

    private CouponPolicy createRewardCouponPolicy() {
        Region region = createRegion();
        return new CouponPolicy(
            createContent(region),
            region,
            "스탬프북 완료 보상",
            "스탬프북 완료 시 발급하는 할인 쿠폰입니다.",
            CouponIssuanceType.STAMPBOOK_COMPLETION,
            3_000,
            10_000,
            30,
            ISSUE_STARTS_AT,
            ISSUE_ENDS_AT,
            100L
        );
    }

    private Region createRegion() {
        return new Region("GIMHAE", "김해시", true);
    }

    private Content createContent(Region region) {
        return new Content(
            region,
            createUser(),
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            ISSUE_STARTS_AT
        );
    }

    private AppUser createUser() {
        return new AppUser(
            "visitor@example.com",
            "hashed-password",
            "방문자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        );
    }
}
