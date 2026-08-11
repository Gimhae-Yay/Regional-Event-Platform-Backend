package io.regionevent.regioneventbackend.domain.coupon.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuance;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionRewardClaim;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRewardClaimRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class MissionRewardCouponRepositoryTest {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant CLAIMED_AT = Instant.parse("2026-08-10T00:01:00Z");

    private final CouponRepository couponRepository;
    private final CouponIssuanceRepository couponIssuanceRepository;
    private final CouponStatusHistoryRepository couponStatusHistoryRepository;
    private final MissionRewardClaimRepository missionRewardClaimRepository;
    private final MissionParticipationRepository missionParticipationRepository;
    private final MissionRepository missionRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;

    @Autowired
    MissionRewardCouponRepositoryTest(
        CouponRepository couponRepository,
        CouponIssuanceRepository couponIssuanceRepository,
        CouponStatusHistoryRepository couponStatusHistoryRepository,
        MissionRewardClaimRepository missionRewardClaimRepository,
        MissionParticipationRepository missionParticipationRepository,
        MissionRepository missionRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager
    ) {
        this.couponRepository = couponRepository;
        this.couponIssuanceRepository = couponIssuanceRepository;
        this.couponStatusHistoryRepository = couponStatusHistoryRepository;
        this.missionRewardClaimRepository = missionRewardClaimRepository;
        this.missionParticipationRepository = missionParticipationRepository;
        this.missionRepository = missionRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 미션_보상_쿠폰_발급과_상태_이력은_발급_식별키로_조회한다() {
        RewardFixtures fixtures = createRewardFixtures();
        MissionRewardClaim missionRewardClaim = missionRewardClaimRepository.saveAndFlush(
            new MissionRewardClaim(fixtures.participation(), fixtures.couponPolicy(), CLAIMED_AT)
        );
        Coupon coupon = couponRepository.saveAndFlush(new Coupon(
            fixtures.couponPolicy(),
            fixtures.visitor(),
            CLAIMED_AT,
            CLAIMED_AT.plusSeconds(2_592_000)
        ));
        CouponIssuance issuance = couponIssuanceRepository.saveAndFlush(new CouponIssuance(
            coupon,
            fixtures.couponPolicy(),
            fixtures.visitor(),
            null,
            missionRewardClaim,
            null,
            "mission-reward-issuance-hash",
            CLAIMED_AT
        ));
        couponStatusHistoryRepository.saveAndFlush(new CouponStatusHistory(
            coupon,
            null,
            CouponStatus.AVAILABLE,
            "MISSION_REWARD_ISSUED",
            "USER",
            CLAIMED_AT
        ));
        entityManager.clear();

        CouponIssuance foundIssuance = couponIssuanceRepository
            .findByIssuanceIdentityHash("mission-reward-issuance-hash")
            .orElseThrow();
        CouponIssuance foundByClaim = couponIssuanceRepository
            .findByMissionRewardClaimMissionRewardClaimId(missionRewardClaim.getMissionRewardClaimId())
            .orElseThrow();
        MissionRewardClaim foundClaim = missionRewardClaimRepository
            .findByMissionParticipationMissionParticipationId(
                fixtures.participation().getMissionParticipationId()
            )
            .orElseThrow();

        assertThat(foundIssuance.getCouponIssuanceId()).isEqualTo(issuance.getCouponIssuanceId());
        assertThat(foundByClaim.getCouponIssuanceId()).isEqualTo(issuance.getCouponIssuanceId());
        assertThat(foundClaim.getMissionRewardClaimId()).isEqualTo(missionRewardClaim.getMissionRewardClaimId());
        assertThat(couponStatusHistoryRepository
            .findAllByCouponCouponIdOrderByOccurredAtAsc(coupon.getCouponId()))
            .extracting(CouponStatusHistory::getNextStatus)
            .containsExactly(CouponStatus.AVAILABLE);
        assertThat(couponStatusHistoryRepository
            .findFirstByCouponCouponIdOrderByOccurredAtAsc(coupon.getCouponId()))
            .hasValueSatisfying(history -> {
                assertThat(history.getPreviousStatus()).isNull();
                assertThat(history.getNextStatus()).isEqualTo(CouponStatus.AVAILABLE);
                assertThat(history.getReasonCode()).isEqualTo("MISSION_REWARD_ISSUED");
                assertThat(history.getActorKind()).isEqualTo("USER");
            });
        assertThat(Hibernate.isInitialized(foundIssuance.getCoupon())).isTrue();
        assertThat(Hibernate.isInitialized(foundIssuance.getMissionRewardClaim())).isTrue();
        assertThat(Hibernate.isInitialized(foundClaim.getMissionParticipation())).isTrue();
        assertThat(Hibernate.isInitialized(foundClaim.getCouponPolicy())).isTrue();
    }

    @Test
    void 미션참여와보상수령은식별자로잠금조회한다() {
        RewardFixtures fixtures = createRewardFixtures();
        MissionRewardClaim claim = missionRewardClaimRepository.saveAndFlush(
            new MissionRewardClaim(fixtures.participation(), fixtures.couponPolicy(), CLAIMED_AT)
        );
        entityManager.clear();

        assertThat(missionParticipationRepository.findByMissionParticipationIdForUpdate(
            fixtures.participation().getMissionParticipationId()
        )).hasValueSatisfying(participation ->
            assertThat(participation.getMissionParticipationId())
                .isEqualTo(fixtures.participation().getMissionParticipationId())
        );
        assertThat(missionRewardClaimRepository.findByMissionParticipationIdForUpdate(
            fixtures.participation().getMissionParticipationId()
        )).hasValueSatisfying(found ->
            assertThat(found.getMissionRewardClaimId()).isEqualTo(claim.getMissionRewardClaimId())
        );
    }

    @Test
    void 미션_참여당_보상_수령은_하나만_저장된다() {
        RewardFixtures fixtures = createRewardFixtures();
        missionRewardClaimRepository.saveAndFlush(new MissionRewardClaim(
            fixtures.participation(),
            fixtures.couponPolicy(),
            CLAIMED_AT
        ));

        assertThatThrownBy(() -> missionRewardClaimRepository.saveAndFlush(new MissionRewardClaim(
            fixtures.participation(),
            fixtures.couponPolicy(),
            CLAIMED_AT.plusSeconds(1)
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 쿠폰_발급은_정확히_하나의_근거와_일치하는_정책을_요구한다() {
        RewardFixtures fixtures = createRewardFixtures();
        Coupon coupon = couponRepository.saveAndFlush(new Coupon(
            fixtures.couponPolicy(),
            fixtures.visitor(),
            CLAIMED_AT,
            CLAIMED_AT.plusSeconds(2_592_000)
        ));

        assertThatThrownBy(() -> new CouponIssuance(
            coupon,
            fixtures.couponPolicy(),
            fixtures.visitor(),
            null,
            null,
            null,
            "missing-source",
            CLAIMED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private RewardFixtures createRewardFixtures() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        AppUser operator = saveUser("operator@example.com", "콘텐츠 운영자");
        AppUser visitor = saveUser("visitor@example.com", "방문자");
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
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
        ));
        CouponPolicy couponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
            content,
            region,
            "미션 완료 보상",
            "미션 완료 시 발급하는 할인 쿠폰입니다.",
            CouponIssuanceType.MISSION_REWARD,
            3_000,
            10_000,
            30,
            ISSUE_STARTS_AT,
            ISSUE_ENDS_AT,
            100L
        ));
        Mission mission = missionRepository.saveAndFlush(new Mission(
            region,
            MissionConditionType.VISIT_COUNT,
            1,
            couponPolicy,
            ISSUE_ENDS_AT
        ));
        MissionParticipation participation = missionParticipationRepository.saveAndFlush(
            new MissionParticipation(mission, visitor, ISSUE_STARTS_AT)
        );
        participation.complete(COMPLETED_AT);
        missionParticipationRepository.flush();

        return new RewardFixtures(visitor, couponPolicy, participation);
    }

    private AppUser saveUser(
        String loginIdentifier,
        String name
    ) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            name,
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private record RewardFixtures(
        AppUser visitor,
        CouponPolicy couponPolicy,
        MissionParticipation participation
    ) {
    }
}
