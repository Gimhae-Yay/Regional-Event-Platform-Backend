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

    private Region region;
    private CouponPolicy rewardCouponPolicy;
    private Mission mission;

    @BeforeEach
    void setUp() {
        region = mock(Region.class);
        rewardCouponPolicy = mock(CouponPolicy.class);
        when(region.getRegionId()).thenReturn(11L);
        when(rewardCouponPolicy.getRegion()).thenReturn(region);
        when(rewardCouponPolicy.getIssuanceType()).thenReturn(CouponIssuanceType.MISSION_REWARD);
        when(rewardCouponPolicy.getStatus()).thenReturn(CouponPolicyStatus.PUBLISHED);
        mission = new Mission(
            "기존 미션",
            region,
            MissionConditionType.VISIT_COUNT,
            3,
            rewardCouponPolicy,
            ENDS_AT
        );
    }

    @Test
    void constructor_withValidTitle_normalizesOuterWhitespaceAndPreservesInnerWhitespace() {
        Mission titledMission = new Mission(
            "  김해   문화 미션  ",
            region,
            MissionConditionType.VISIT_COUNT,
            3,
            rewardCouponPolicy,
            ENDS_AT
        );

        assertThat(titledMission.getTitle()).isEqualTo("김해   문화 미션");
    }

    @Test
    void constructor_withSupplementaryUnicodeTitle_validatesByCodePoint() {
        String maximumTitle = "😀".repeat(255);

        Mission titledMission = new Mission(
            maximumTitle,
            region,
            MissionConditionType.VISIT_COUNT,
            3,
            rewardCouponPolicy,
            ENDS_AT
        );

        assertThat(titledMission.getTitle()).isEqualTo(maximumTitle);
        assertThatThrownBy(() -> new Mission(
            "😀".repeat(256),
            region,
            MissionConditionType.VISIT_COUNT,
            3,
            rewardCouponPolicy,
            ENDS_AT
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("title must contain 1 to 255 Unicode code points");
    }

    @Test
    void constructor_withBlankTitle_rejectsTitle() {
        assertThatThrownBy(() -> new Mission(
            "   ",
            region,
            MissionConditionType.VISIT_COUNT,
            3,
            rewardCouponPolicy,
            ENDS_AT
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("title must contain 1 to 255 Unicode code points");
    }

    @Test
    void fillMissingTitleWithFallback_afterIdAssignment_usesMissionId() {
        Mission untitledMission = new Mission(
            null,
            region,
            MissionConditionType.VISIT_COUNT,
            3,
            rewardCouponPolicy,
            ENDS_AT
        );
        ReflectionTestUtils.setField(untitledMission, "missionId", 701L);

        untitledMission.fillMissingTitleWithFallback();

        assertThat(untitledMission.getTitle()).isEqualTo("미션 701");
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

    @Test
    void replaceDraftCoreValues_whenDraft_replacesAllEditableValues() {
        Region region = mission.getRegion();
        CouponPolicy replacementPolicy = mock(CouponPolicy.class);
        when(replacementPolicy.getRegion()).thenReturn(region);
        when(replacementPolicy.getIssuanceType()).thenReturn(CouponIssuanceType.MISSION_REWARD);
        when(replacementPolicy.getStatus()).thenReturn(CouponPolicyStatus.DRAFT);
        Instant replacementEndsAt = ENDS_AT.plusSeconds(3600);

        mission.replaceDraftCoreValues(
            "  변경   제목  ",
            MissionConditionType.CONTENT_SET,
            null,
            replacementPolicy,
            replacementEndsAt
        );

        assertThat(mission.getTitle()).isEqualTo("변경   제목");
        assertThat(mission.getConditionType()).isEqualTo(MissionConditionType.CONTENT_SET);
        assertThat(mission.getRequiredVisitCount()).isNull();
        assertThat(mission.getRewardCouponPolicy()).isSameAs(replacementPolicy);
        assertThat(mission.getEndsAt()).isEqualTo(replacementEndsAt);
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.DRAFT);
    }

    @Test
    void replaceDraftCoreValues_withNullTitle_preservesExistingTitle() {
        mission.replaceDraftCoreValues(
            null,
            MissionConditionType.VISIT_COUNT,
            4,
            rewardCouponPolicy,
            ENDS_AT.plusSeconds(3600)
        );

        assertThat(mission.getTitle()).isEqualTo("기존 미션");
    }

    @Test
    void replaceDraftCoreValues_whenNotDraft_rejectsUpdateWithoutChangingValues() {
        ReflectionTestUtils.setField(mission, "status", MissionStatus.PENDING_REVIEW);
        CouponPolicy originalPolicy = mission.getRewardCouponPolicy();

        assertThatThrownBy(() -> mission.replaceDraftCoreValues(
            "변경 제목",
            MissionConditionType.CONTENT_SET,
            null,
            originalPolicy,
            ENDS_AT.plusSeconds(3600)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("mission status must be DRAFT but was PENDING_REVIEW");
        assertThat(mission.getTitle()).isEqualTo("기존 미션");
        assertThat(mission.getConditionType()).isEqualTo(MissionConditionType.VISIT_COUNT);
        assertThat(mission.getRequiredVisitCount()).isEqualTo(3);
        assertThat(mission.getRewardCouponPolicy()).isSameAs(originalPolicy);
        assertThat(mission.getEndsAt()).isEqualTo(ENDS_AT);
    }
}
