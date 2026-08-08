package io.regionevent.regioneventbackend.domain.mission.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionTargetContent;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class MissionRepositoryTest {

    private static final Instant CONTENT_PUBLISHED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant CONTENT_DELETED_AT = Instant.parse("2026-08-02T00:00:00Z");
    private static final Instant COUPON_ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant MISSION_ENDS_AT = Instant.parse("2026-09-01T00:00:00Z");

    private final MissionRepository missionRepository;
    private final MissionTargetContentRepository missionTargetContentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;

    @Autowired
    MissionRepositoryTest(
        MissionRepository missionRepository,
        MissionTargetContentRepository missionTargetContentRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager
    ) {
        this.missionRepository = missionRepository;
        this.missionTargetContentRepository = missionTargetContentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 콘텐츠_세트_미션은_지역_보상_정책과_대상_콘텐츠를_저장하고_정렬_잠금_조회한다() {
        Region region = saveRegion("GIMHAE");
        Content rewardContent = saveContent(region, "reward");
        Content firstTargetContent = saveContent(region, "first-target");
        Content secondTargetContent = saveContent(region, "second-target");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, region);
        Mission mission = new Mission(
            region,
            MissionConditionType.CONTENT_SET,
            null,
            rewardCouponPolicy,
            MISSION_ENDS_AT
        );
        mission.addTargetContent(secondTargetContent);
        mission.addTargetContent(firstTargetContent);
        Mission savedMission = missionRepository.saveAndFlush(mission);
        entityManager.clear();

        Mission foundMission = missionRepository.findByMissionId(savedMission.getMissionId()).orElseThrow();
        List<MissionTargetContent> targetContents = missionTargetContentRepository
            .findAllByMissionIdOrderByContentIdAsc(savedMission.getMissionId());
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(foundMission.getRegion().getRegionId()).isEqualTo(region.getRegionId());
        assertThat(foundMission.getConditionType()).isEqualTo(MissionConditionType.CONTENT_SET);
        assertThat(foundMission.getRequiredVisitCount()).isNull();
        assertThat(foundMission.getRewardCouponPolicy().getCouponPolicyId())
            .isEqualTo(rewardCouponPolicy.getCouponPolicyId());
        assertThat(foundMission.getStatus()).isEqualTo(MissionStatus.DRAFT);
        assertThat(foundMission.getEndsAt()).isEqualTo(MISSION_ENDS_AT);
        assertThat(persistenceUnitUtil.isLoaded(foundMission, "region")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(foundMission, "rewardCouponPolicy")).isTrue();
        assertThat(targetContents)
            .extracting(targetContent -> targetContent.getContent().getContentId())
            .containsExactly(firstTargetContent.getContentId(), secondTargetContent.getContentId());
        assertThat(targetContents)
            .allSatisfy(targetContent -> {
                assertThat(targetContent.getMission().getMissionId()).isEqualTo(savedMission.getMissionId());
                assertThat(persistenceUnitUtil.isLoaded(targetContent, "mission")).isTrue();
                assertThat(persistenceUnitUtil.isLoaded(targetContent, "content")).isTrue();
            });
        assertThat(missionRepository.findByMissionIdForUpdate(savedMission.getMissionId()))
            .contains(foundMission);
        assertThat(missionTargetContentRepository
            .findAllByMissionIdForUpdateOrderByContentIdAsc(savedMission.getMissionId()))
            .extracting(targetContent -> targetContent.getContent().getContentId())
            .containsExactly(firstTargetContent.getContentId(), secondTargetContent.getContentId());
    }

    @Test
    void 방문_횟수_미션은_양수_목표를_저장하고_대상_콘텐츠를_추가하지_않는다() {
        Region region = saveRegion("GIMHAE");
        Content rewardContent = saveContent(region, "reward");
        Content targetContent = saveContent(region, "target");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, region);
        Mission mission = missionRepository.saveAndFlush(new Mission(
            region,
            MissionConditionType.VISIT_COUNT,
            3,
            rewardCouponPolicy,
            MISSION_ENDS_AT
        ));
        entityManager.clear();

        Mission foundMission = missionRepository.findByMissionId(mission.getMissionId()).orElseThrow();

        assertThat(foundMission.getRequiredVisitCount()).isEqualTo(3);
        assertThat(missionTargetContentRepository
            .findAllByMissionIdOrderByContentIdAsc(mission.getMissionId()))
            .isEmpty();
        assertThatThrownBy(() -> foundMission.addTargetContent(targetContent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("only CONTENT_SET can add target contents");
        assertThatThrownBy(() -> missionTargetContentRepository.saveAndFlush(
            new MissionTargetContent(foundMission, targetContent)
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("only CONTENT_SET can add target contents");
        assertThatThrownBy(() -> new Mission(
            region,
            MissionConditionType.VISIT_COUNT,
            null,
            rewardCouponPolicy,
            MISSION_ENDS_AT
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("VISIT_COUNT requires a positive requiredVisitCount");
        assertThatThrownBy(() -> new Mission(
            region,
            MissionConditionType.CONTENT_SET,
            1,
            rewardCouponPolicy,
            MISSION_ENDS_AT
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("CONTENT_SET must not have requiredVisitCount");
    }

    @Test
    void 미션은_동일_지역의_MISSION_REWARD_쿠폰_정책과_대상_콘텐츠만_참조한다() {
        Region region = saveRegion("GIMHAE");
        Region anotherRegion = saveRegion("BUSAN");
        Content rewardContent = saveContent(region, "reward");
        Content anotherRegionContent = saveContent(anotherRegion, "another-region");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, region);
        CouponPolicy anotherRegionRewardCouponPolicy = saveMissionRewardCouponPolicy(
            anotherRegionContent,
            anotherRegion
        );
        CouponPolicy visitCouponPolicy = newCouponPolicy(
            rewardContent,
            region,
            CouponIssuanceType.VISIT
        );
        Mission mission = new Mission(
            region,
            MissionConditionType.CONTENT_SET,
            null,
            rewardCouponPolicy,
            MISSION_ENDS_AT
        );

        assertThatThrownBy(() -> mission.addTargetContent(anotherRegionContent))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("content must belong to the mission region");
        assertThatThrownBy(() -> new Mission(
            region,
            MissionConditionType.CONTENT_SET,
            null,
            anotherRegionRewardCouponPolicy,
            MISSION_ENDS_AT
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("rewardCouponPolicy must belong to the mission region");
        assertThatThrownBy(() -> new Mission(
            region,
            MissionConditionType.CONTENT_SET,
            null,
            visitCouponPolicy,
            MISSION_ENDS_AT
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("rewardCouponPolicy must use MISSION_REWARD issuance type");
    }

    @Test
    void 삭제된_콘텐츠는_콘텐츠_세트_미션의_대상으로_연결하거나_저장할_수_없다() {
        Region region = saveRegion("GIMHAE");
        Content rewardContent = saveContent(region, "reward");
        Content deletedTargetContent = saveContent(region, "deleted-target", ContentStatus.APPROVED);
        deletedTargetContent.softDelete(CONTENT_DELETED_AT);
        contentRepository.saveAndFlush(deletedTargetContent);
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, region);
        Mission mission = missionRepository.saveAndFlush(new Mission(
            region,
            MissionConditionType.CONTENT_SET,
            null,
            rewardCouponPolicy,
            MISSION_ENDS_AT
        ));

        assertThatThrownBy(() -> mission.addTargetContent(deletedTargetContent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("soft deleted content cannot be a mission target");
        assertThatThrownBy(() -> missionTargetContentRepository.saveAndFlush(
            new MissionTargetContent(mission, deletedTargetContent)
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("soft deleted content cannot be a mission target");
    }

    private Region saveRegion(String regionCode) {
        return regionRepository.saveAndFlush(new Region(regionCode, regionCode + "시", true));
    }

    private Content saveContent(
        Region region,
        String suffix
    ) {
        return saveContent(region, suffix, ContentStatus.PUBLISHED);
    }

    private Content saveContent(
        Region region,
        String suffix,
        ContentStatus status
    ) {
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator-" + region.getRegionCode() + "-" + suffix + "@example.com",
            "hashed-password",
            "콘텐츠 운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
            suffix + " 콘텐츠",
            "미션 대상 콘텐츠 영속성 검증을 위한 설명입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            CONTENT_PUBLISHED_AT
        ));
    }

    private CouponPolicy saveMissionRewardCouponPolicy(
        Content content,
        Region region
    ) {
        return couponPolicyRepository.saveAndFlush(newCouponPolicy(
            content,
            region,
            CouponIssuanceType.MISSION_REWARD
        ));
    }

    private CouponPolicy newCouponPolicy(
        Content content,
        Region region,
        CouponIssuanceType issuanceType
    ) {
        return new CouponPolicy(
            content,
            region,
            "미션 완주 쿠폰",
            "미션 완료 보상 쿠폰입니다.",
            issuanceType,
            3_000,
            10_000,
            30,
            CONTENT_PUBLISHED_AT,
            COUPON_ISSUE_ENDS_AT,
            100L
        );
    }
}
