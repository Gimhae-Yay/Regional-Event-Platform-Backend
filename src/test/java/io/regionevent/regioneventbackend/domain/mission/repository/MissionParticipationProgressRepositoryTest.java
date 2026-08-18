package io.regionevent.regioneventbackend.domain.mission.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionProgress;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionRewardClaim;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class MissionParticipationProgressRepositoryTest {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant MISSION_ENDS_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant JOINED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-12T00:00:00Z");
    private static final Instant RECORDED_AT = Instant.parse("2026-08-11T00:00:00Z");

    private final MissionParticipationRepository missionParticipationRepository;
    private final MissionProgressRepository missionProgressRepository;
    private final MissionTargetContentRepository missionTargetContentRepository;
    private final MissionRewardClaimRepository missionRewardClaimRepository;
    private final MissionRepository missionRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final RegionRepository regionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AppUserRepository appUserRepository;
    private final VisitRepository visitRepository;
    private final EntityManager entityManager;

    @Autowired
    MissionParticipationProgressRepositoryTest(
        MissionParticipationRepository missionParticipationRepository,
        MissionProgressRepository missionProgressRepository,
        MissionTargetContentRepository missionTargetContentRepository,
        MissionRewardClaimRepository missionRewardClaimRepository,
        MissionRepository missionRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        RegionRepository regionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        AppUserRepository appUserRepository,
        VisitRepository visitRepository,
        EntityManager entityManager
    ) {
        this.missionParticipationRepository = missionParticipationRepository;
        this.missionProgressRepository = missionProgressRepository;
        this.missionTargetContentRepository = missionTargetContentRepository;
        this.missionRewardClaimRepository = missionRewardClaimRepository;
        this.missionRepository = missionRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.regionRepository = regionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.appUserRepository = appUserRepository;
        this.visitRepository = visitRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 참여는_미션과_사용자별로_조회와_잠금_조회가_가능하다() {
        MissionFixtures fixtures = createMissionFixtures();
        MissionParticipation participation = missionParticipationRepository.saveAndFlush(
            new MissionParticipation(fixtures.mission(), fixtures.visitor(), JOINED_AT)
        );
        entityManager.clear();

        MissionParticipation foundParticipation = missionParticipationRepository
            .findByMissionMissionIdAndUserUserId(
                fixtures.mission().getMissionId(),
                fixtures.visitor().getUserId()
            )
            .orElseThrow();
        MissionParticipation lockedParticipation = missionParticipationRepository
            .findByMissionIdAndUserIdForUpdate(
                fixtures.mission().getMissionId(),
                fixtures.visitor().getUserId()
            )
            .orElseThrow();
        List<MissionParticipation> candidates = missionParticipationRepository
            .findAllByUserIdAndRegionIdAndStatus(
                fixtures.visitor().getUserId(),
                fixtures.region().getRegionId(),
                MissionParticipationStatus.IN_PROGRESS
            );

        assertThat(foundParticipation.getMissionParticipationId())
            .isEqualTo(participation.getMissionParticipationId());
        assertThat(lockedParticipation.getJoinedAt()).isEqualTo(JOINED_AT);
        assertThat(candidates)
            .extracting(MissionParticipation::getMissionParticipationId)
            .containsExactly(participation.getMissionParticipationId());
    }

    @Test
    void 같은_사용자는_같은_미션에_한번만_참여할_수_있다() {
        MissionFixtures fixtures = createMissionFixtures();
        missionParticipationRepository.saveAndFlush(
            new MissionParticipation(fixtures.mission(), fixtures.visitor(), JOINED_AT)
        );

        assertThatThrownBy(() -> missionParticipationRepository.saveAndFlush(
            new MissionParticipation(fixtures.mission(), fixtures.visitor(), JOINED_AT)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 종료_처리를_위한_잠금_조회는_진행_중_참여만_식별자_순으로_반환한다() {
        MissionFixtures fixtures = createMissionFixtures();
        AppUser completedVisitor = saveUser("completed-visitor@example.com", "완료 사용자");
        AppUser firstInProgressVisitor = saveUser("first-visitor@example.com", "첫 진행 사용자");
        AppUser secondInProgressVisitor = saveUser("second-visitor@example.com", "두번째 진행 사용자");
        MissionParticipation completedParticipation = missionParticipationRepository.saveAndFlush(
            new MissionParticipation(fixtures.mission(), completedVisitor, JOINED_AT)
        );
        completedParticipation.complete(COMPLETED_AT);
        MissionParticipation firstInProgressParticipation = missionParticipationRepository.saveAndFlush(
            new MissionParticipation(fixtures.mission(), firstInProgressVisitor, JOINED_AT)
        );
        MissionParticipation secondInProgressParticipation = missionParticipationRepository.saveAndFlush(
            new MissionParticipation(fixtures.mission(), secondInProgressVisitor, JOINED_AT)
        );
        missionParticipationRepository.flush();
        entityManager.clear();

        List<MissionParticipation> inProgressParticipations = missionParticipationRepository
            .findAllByMissionIdAndStatusForUpdate(
                fixtures.mission().getMissionId(),
                MissionParticipationStatus.IN_PROGRESS
            );
        inProgressParticipations.forEach(MissionParticipation::endIncomplete);
        missionParticipationRepository.flush();

        assertThat(inProgressParticipations)
            .extracting(MissionParticipation::getMissionParticipationId)
            .containsExactly(
                firstInProgressParticipation.getMissionParticipationId(),
                secondInProgressParticipation.getMissionParticipationId()
            );
        assertThat(missionParticipationRepository.findById(completedParticipation.getMissionParticipationId()).orElseThrow()
            .getStatus()).isEqualTo(MissionParticipationStatus.COMPLETED);
        assertThat(missionParticipationRepository.findById(firstInProgressParticipation.getMissionParticipationId())
            .orElseThrow().getStatus()).isEqualTo(MissionParticipationStatus.ENDED_INCOMPLETE);
    }

    @Test
    void 진행_근거는_참여별_방문과_콘텐츠로_조회할_수_있다() {
        ProgressFixtures fixtures = createProgressFixtures();
        MissionProgress progress = missionProgressRepository.saveAndFlush(new MissionProgress(
            fixtures.participation(),
            fixtures.visit(),
            fixtures.content(),
            RECORDED_AT
        ));
        entityManager.clear();

        assertThat(missionProgressRepository
            .existsByMissionParticipationMissionParticipationIdAndVisitVisitId(
                fixtures.participation().getMissionParticipationId(),
                fixtures.visit().getVisitId()
            )).isTrue();
        assertThat(missionProgressRepository
            .existsByMissionParticipationMissionParticipationIdAndContentContentId(
                fixtures.participation().getMissionParticipationId(),
                fixtures.content().getContentId()
            )).isTrue();
        assertThat(missionProgressRepository.countByMissionParticipationMissionParticipationId(
            fixtures.participation().getMissionParticipationId()
        )).isOne();
        assertThat(missionProgressRepository.findAllByMissionParticipationIdOrderByRecordedAtAsc(
            fixtures.participation().getMissionParticipationId()
        )).extracting(MissionProgress::getId).containsExactly(progress.getId());
    }

    @Test
    void 같은_참여에_같은_방문_근거는_한번만_저장된다() {
        ProgressFixtures fixtures = createProgressFixtures();
        missionProgressRepository.saveAndFlush(new MissionProgress(
            fixtures.participation(),
            fixtures.visit(),
            fixtures.content(),
            RECORDED_AT
        ));

        assertThatThrownBy(() -> missionProgressRepository.saveAndFlush(new MissionProgress(
            fixtures.participation(),
            fixtures.visit(),
            fixtures.content(),
            RECORDED_AT
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 콘텐츠_집합_대상은_미션과_콘텐츠로_포함여부와_목표수를_조회한다() {
        MissionFixtures fixtures = createMissionFixtures();
        Mission contentSetMission = new Mission(
            "테스트 미션",
            fixtures.region(),
            MissionConditionType.CONTENT_SET,
            null,
            fixtures.mission().getRewardCouponPolicy(),
            MISSION_ENDS_AT
        );
        contentSetMission.addTargetContent(fixtures.content());
        missionRepository.saveAndFlush(contentSetMission);
        entityManager.clear();

        assertThat(missionTargetContentRepository.existsByMissionMissionIdAndContentContentId(
            contentSetMission.getMissionId(),
            fixtures.content().getContentId()
        )).isTrue();
        assertThat(missionTargetContentRepository.countByMissionMissionId(
            contentSetMission.getMissionId()
        )).isOne();
    }

    @Test
    void 참여_요약은_본인_상태_고정정렬_페이지와_집계를_반환한다() {
        MissionFixtures fixtures = createMissionFixtures();
        Mission secondMission = saveVisitCountMission(fixtures, 2);
        Mission thirdMission = saveVisitCountMission(fixtures, 1);
        Mission otherUserMission = saveVisitCountMission(fixtures, 1);
        MissionParticipation completedParticipation = missionParticipationRepository.saveAndFlush(
            new MissionParticipation(fixtures.mission(), fixtures.visitor(), JOINED_AT)
        );
        completedParticipation.complete(COMPLETED_AT);
        MissionParticipation firstTiedParticipation = missionParticipationRepository.saveAndFlush(
            new MissionParticipation(secondMission, fixtures.visitor(), JOINED_AT.plusSeconds(60))
        );
        MissionParticipation secondTiedParticipation = missionParticipationRepository.saveAndFlush(
            new MissionParticipation(thirdMission, fixtures.visitor(), JOINED_AT.plusSeconds(60))
        );
        AppUser otherVisitor = saveUser("other-summary-visitor@example.com", "다른 방문자");
        missionParticipationRepository.saveAndFlush(
            new MissionParticipation(otherUserMission, otherVisitor, JOINED_AT.plusSeconds(120))
        );
        Visit firstVisit = saveVisit(fixtures, "summary-first");
        Visit secondVisit = saveVisit(fixtures, "summary-second");
        missionProgressRepository.saveAndFlush(new MissionProgress(
            completedParticipation,
            firstVisit,
            fixtures.content(),
            RECORDED_AT
        ));
        missionProgressRepository.saveAndFlush(new MissionProgress(
            completedParticipation,
            secondVisit,
            fixtures.content(),
            RECORDED_AT.plusSeconds(60)
        ));
        missionRewardClaimRepository.saveAndFlush(new MissionRewardClaim(
            completedParticipation,
            fixtures.mission().getRewardCouponPolicy(),
            COMPLETED_AT.plusSeconds(60)
        ));
        entityManager.clear();

        Page<MissionParticipationSummaryProjection> firstPage = missionParticipationRepository
            .findSummariesByUserIdAndStatus(
                fixtures.visitor().getUserId(),
                null,
                PageRequest.of(0, 2)
            );
        Page<MissionParticipationSummaryProjection> secondPage = missionParticipationRepository
            .findSummariesByUserIdAndStatus(
                fixtures.visitor().getUserId(),
                null,
                PageRequest.of(1, 2)
            );
        Page<MissionParticipationSummaryProjection> completedPage = missionParticipationRepository
            .findSummariesByUserIdAndStatus(
                fixtures.visitor().getUserId(),
                MissionParticipationStatus.COMPLETED,
                PageRequest.of(0, 20)
            );

        assertThat(firstPage.getContent())
            .extracting(MissionParticipationSummaryProjection::getParticipationId)
            .containsExactly(
                secondTiedParticipation.getMissionParticipationId(),
                firstTiedParticipation.getMissionParticipationId()
            );
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(secondPage.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.getParticipationId()).isEqualTo(completedParticipation.getMissionParticipationId());
            assertThat(summary.getMissionId()).isEqualTo(fixtures.mission().getMissionId());
            assertThat(summary.getStatus()).isEqualTo(MissionParticipationStatus.COMPLETED);
            assertThat(summary.getProgressCount()).isEqualTo(2);
            assertThat(summary.getRequiredCount()).isEqualTo(3);
            assertThat(summary.getRewardClaimed()).isTrue();
            assertThat(summary.getJoinedAt()).isEqualTo(JOINED_AT);
            assertThat(summary.getCompletedAt()).isEqualTo(COMPLETED_AT);
        });
        assertThat(completedPage.getContent())
            .extracting(MissionParticipationSummaryProjection::getParticipationId)
            .containsExactly(completedParticipation.getMissionParticipationId());
    }

    @Test
    void 콘텐츠_집합_참여_요약은_서로_다른_진행_콘텐츠와_목표_콘텐츠를_집계한다() {
        MissionFixtures fixtures = createMissionFixtures();
        Content secondContent = saveContent(
            fixtures.region(),
            fixtures.operator(),
            "김해 박물관 체험",
            "content-set-second"
        );
        Mission contentSetMission = new Mission(
            "테스트 미션",
            fixtures.region(),
            MissionConditionType.CONTENT_SET,
            null,
            fixtures.mission().getRewardCouponPolicy(),
            MISSION_ENDS_AT
        );
        contentSetMission.addTargetContent(fixtures.content());
        contentSetMission.addTargetContent(secondContent);
        missionRepository.saveAndFlush(contentSetMission);
        MissionParticipation participation = missionParticipationRepository.saveAndFlush(
            new MissionParticipation(contentSetMission, fixtures.visitor(), JOINED_AT)
        );
        Visit firstContentFirstVisit = saveVisit(fixtures, fixtures.content(), "set-first");
        Visit firstContentSecondVisit = saveVisit(fixtures, fixtures.content(), "set-duplicate");
        Visit secondContentVisit = saveVisit(fixtures, secondContent, "set-second");
        missionProgressRepository.saveAndFlush(new MissionProgress(
            participation,
            firstContentFirstVisit,
            fixtures.content(),
            RECORDED_AT
        ));
        missionProgressRepository.saveAndFlush(new MissionProgress(
            participation,
            firstContentSecondVisit,
            fixtures.content(),
            RECORDED_AT.plusSeconds(60)
        ));
        missionProgressRepository.saveAndFlush(new MissionProgress(
            participation,
            secondContentVisit,
            secondContent,
            RECORDED_AT.plusSeconds(120)
        ));
        entityManager.clear();

        MissionParticipationSummaryProjection summary = missionParticipationRepository
            .findSummariesByUserIdAndStatus(
                fixtures.visitor().getUserId(),
                null,
                PageRequest.of(0, 20)
            )
            .getContent()
            .getFirst();

        assertThat(summary.getProgressCount()).isEqualTo(2);
        assertThat(summary.getRequiredCount()).isEqualTo(2);
        assertThat(summary.getRewardClaimed()).isFalse();
        MissionParticipationSummaryProjection singleSummary = missionParticipationRepository
            .findSummaryByMissionIdAndUserId(
                contentSetMission.getMissionId(),
                fixtures.visitor().getUserId()
            )
            .orElseThrow();
        assertThat(singleSummary.getParticipationId()).isEqualTo(participation.getMissionParticipationId());
        assertThat(singleSummary.getProgressCount()).isEqualTo(2);
        assertThat(singleSummary.getRequiredCount()).isEqualTo(2);
    }

    @Test
    void 방문_횟수_참여_요약은_서로_다른_방문과_보상_수령_여부를_집계한다() {
        MissionFixtures fixtures = createMissionFixtures();
        MissionParticipation participation = missionParticipationRepository.saveAndFlush(
            new MissionParticipation(fixtures.mission(), fixtures.visitor(), JOINED_AT)
        );
        Visit firstVisit = saveVisit(fixtures, "single-summary-first");
        Visit secondVisit = saveVisit(fixtures, "single-summary-second");
        missionProgressRepository.saveAndFlush(new MissionProgress(
            participation,
            firstVisit,
            fixtures.content(),
            RECORDED_AT
        ));
        missionProgressRepository.saveAndFlush(new MissionProgress(
            participation,
            secondVisit,
            fixtures.content(),
            RECORDED_AT.plusSeconds(60)
        ));
        participation.complete(COMPLETED_AT);
        missionParticipationRepository.flush();
        missionRewardClaimRepository.saveAndFlush(new MissionRewardClaim(
            participation,
            fixtures.mission().getRewardCouponPolicy(),
            COMPLETED_AT.plusSeconds(60)
        ));
        entityManager.clear();

        MissionParticipationSummaryProjection summary = missionParticipationRepository
            .findSummaryByMissionIdAndUserId(
                fixtures.mission().getMissionId(),
                fixtures.visitor().getUserId()
            )
            .orElseThrow();

        assertThat(summary.getProgressCount()).isEqualTo(2);
        assertThat(summary.getRequiredCount()).isEqualTo(3);
        assertThat(summary.getRewardClaimed()).isTrue();
    }

    private ProgressFixtures createProgressFixtures() {
        MissionFixtures missionFixtures = createMissionFixtures();
        MissionParticipation participation = missionParticipationRepository.saveAndFlush(
            new MissionParticipation(missionFixtures.mission(), missionFixtures.visitor(), JOINED_AT)
        );
        Visit visit = saveVisit(missionFixtures, "progress-visit");

        return new ProgressFixtures(participation, visit, missionFixtures.content());
    }

    private MissionFixtures createMissionFixtures() {
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
        CouponPolicy rewardCouponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
            content,
            region,
            "미션 완료 보상",
            "미션 완료 시 지급하는 쿠폰입니다.",
            CouponIssuanceType.MISSION_REWARD,
            3_000,
            10_000,
            30,
            ISSUE_STARTS_AT,
            ISSUE_ENDS_AT,
            100L
        ));
        Mission mission = missionRepository.saveAndFlush(new Mission(
            "테스트 미션",
            region,
            MissionConditionType.VISIT_COUNT,
            3,
            rewardCouponPolicy,
            MISSION_ENDS_AT
        ));

        return new MissionFixtures(region, content, visitor, operator, mission);
    }

    private Visit saveVisit(
        MissionFixtures fixtures,
        String suffix
    ) {
        return saveVisit(fixtures, fixtures.content(), suffix);
    }

    private Visit saveVisit(
        MissionFixtures fixtures,
        Content content,
        String suffix
    ) {
        AppUser reviewer = saveUser("reviewer-" + suffix + "@example.com", "회차 검토자");
        ContentSession contentSession = new ContentSession(
            content,
            fixtures.region(),
            Instant.parse("2026-08-11T01:00:00Z"),
            Instant.parse("2026-08-11T03:00:00Z"),
            Instant.parse("2026-08-11T00:30:00Z"),
            Instant.parse("2026-08-11T02:30:00Z"),
            10
        );
        contentSession.approve(reviewer, ISSUE_STARTS_AT);
        ContentSession savedContentSession = contentSessionRepository.saveAndFlush(contentSession);
        CapacityHold capacityHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            fixtures.region(),
            savedContentSession,
            fixtures.visitor(),
            1,
            CapacityHoldStatus.CONSUMED,
            ISSUE_STARTS_AT,
            ISSUE_STARTS_AT,
            null,
            null
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-" + suffix,
            "qr-" + suffix,
            fixtures.region(),
            capacityHold,
            savedContentSession,
            fixtures.visitor(),
            ReservationStatus.CONFIRMED,
            ISSUE_STARTS_AT,
            null,
            null,
            null,
            null
        ));

        return visitRepository.saveAndFlush(new Visit(
            fixtures.region(),
            reservation,
            fixtures.visitor(),
            content,
            savedContentSession,
            fixtures.operator(),
            CheckinMethod.QR,
            RECORDED_AT
        ));
    }

    private Mission saveVisitCountMission(MissionFixtures fixtures, int requiredVisitCount) {
        return missionRepository.saveAndFlush(new Mission(
            "테스트 미션",
            fixtures.region(),
            MissionConditionType.VISIT_COUNT,
            requiredVisitCount,
            fixtures.mission().getRewardCouponPolicy(),
            MISSION_ENDS_AT
        ));
    }

    private Content saveContent(
        Region region,
        AppUser operator,
        String title,
        String suffix
    ) {
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            title,
            "미션 목표 콘텐츠입니다.",
            "김해시",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            ISSUE_STARTS_AT.plusSeconds(Math.abs(suffix.hashCode()))
        ));
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

    private record MissionFixtures(
        Region region,
        Content content,
        AppUser visitor,
        AppUser operator,
        Mission mission
    ) {
    }

    private record ProgressFixtures(
        MissionParticipation participation,
        Visit visit,
        Content content
    ) {
    }
}
