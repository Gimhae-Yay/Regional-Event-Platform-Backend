package io.regionevent.regioneventbackend.domain.mission.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;

class MissionTest {

    private static final Instant ENDS_AT = Instant.parse("2026-09-30T14:59:59Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-10T04:30:00Z");
    private static final Instant ENDED_AT = Instant.parse("2026-08-11T04:30:00Z");

    private Mission mission;

    @BeforeEach
    void setUp() {
        Region region = mock(Region.class);
        CouponPolicy rewardCouponPolicy = mock(CouponPolicy.class);
        when(region.getRegionId()).thenReturn(11L);
        when(rewardCouponPolicy.getRegion()).thenReturn(region);
        when(rewardCouponPolicy.getIssuanceType()).thenReturn(CouponIssuanceType.MISSION_REWARD);
        when(rewardCouponPolicy.getStatus()).thenReturn(CouponPolicyStatus.PUBLISHED);
        mission = new Mission(
            region,
            MissionConditionType.VISIT_COUNT,
            3,
            rewardCouponPolicy,
            ENDS_AT
        );
    }

    @Test
    void approve_whenPendingReviewAndBeforeEnd_publishesMission() {
        ReflectionTestUtils.setField(mission, "status", MissionStatus.PENDING_REVIEW);

        mission.approve(PUBLISHED_AT);

        assertThat(mission.getStatus()).isEqualTo(MissionStatus.PUBLISHED);
        assertThat(mission.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(mission.getEndedAt()).isNull();
    }

    @Test
    void approve_whenStatusIsNotPendingReview_rejectsTransition() {
        assertThatThrownBy(() -> mission.approve(PUBLISHED_AT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("mission status must be PENDING_REVIEW");
    }

    @Test
    void approve_whenPublishedAtIsNotBeforeEnd_rejectsTransition() {
        ReflectionTestUtils.setField(mission, "status", MissionStatus.PENDING_REVIEW);

        assertThatThrownBy(() -> mission.approve(ENDS_AT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("publishedAt must be before endsAt");
    }

    @Test
    void reject_whenPendingReview_returnsMissionToDraft() {
        ReflectionTestUtils.setField(mission, "status", MissionStatus.PENDING_REVIEW);

        mission.reject();

        assertThat(mission.getStatus()).isEqualTo(MissionStatus.DRAFT);
        assertThat(mission.getPublishedAt()).isNull();
        assertThat(mission.getEndedAt()).isNull();
    }

    @Test
    void reject_whenStatusIsNotPendingReview_rejectsTransition() {
        assertThatThrownBy(mission::reject)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("mission status must be PENDING_REVIEW");
    }

    @Test
    void end_whenPublished_endsMissionAtGivenTime() {
        ReflectionTestUtils.setField(mission, "status", MissionStatus.PUBLISHED);
        ReflectionTestUtils.setField(mission, "publishedAt", PUBLISHED_AT);

        mission.end(ENDED_AT);

        assertThat(mission.getStatus()).isEqualTo(MissionStatus.ENDED);
        assertThat(mission.getEndedAt()).isEqualTo(ENDED_AT);
    }

    @Test
    void end_whenStatusIsNotPublished_rejectsTransition() {
        assertThatThrownBy(() -> mission.end(ENDED_AT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("mission status must be PUBLISHED");
    }

    @Test
    void end_whenEndedAtIsNull_rejectsTransition() {
        ReflectionTestUtils.setField(mission, "status", MissionStatus.PUBLISHED);

        assertThatThrownBy(() -> mission.end(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("endedAt must not be null");
    }
}
