package io.regionevent.regioneventbackend.domain.mission.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionProgressRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRewardClaimRepository;
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
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyMissionParticipationDetailControllerIntegrationTest {

    private static final Instant CONTENT_PUBLISHED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant COUPON_ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant MISSION_ENDS_AT = Instant.parse("2026-09-30T14:59:59Z");
    private static final Instant JOINED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-13T00:00:00Z");
    private static final Instant FIRST_RECORDED_AT = Instant.parse("2026-08-11T00:00:00Z");
    private static final Instant SECOND_RECORDED_AT = Instant.parse("2026-08-12T00:00:00Z");

    private final MockMvc mockMvc;
    private final MissionRepository missionRepository;
    private final MissionParticipationRepository missionParticipationRepository;
    private final MissionProgressRepository missionProgressRepository;
    private final MissionRewardClaimRepository missionRewardClaimRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final RegionRepository regionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final VisitRepository visitRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    MyMissionParticipationDetailControllerIntegrationTest(
        MockMvc mockMvc,
        MissionRepository missionRepository,
        MissionParticipationRepository missionParticipationRepository,
        MissionProgressRepository missionProgressRepository,
        MissionRewardClaimRepository missionRewardClaimRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        RegionRepository regionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        VisitRepository visitRepository,
        JwtAccessTokenService jwtAccessTokenService,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.missionRepository = missionRepository;
        this.missionParticipationRepository = missionParticipationRepository;
        this.missionProgressRepository = missionProgressRepository;
        this.missionRewardClaimRepository = missionRewardClaimRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.regionRepository = regionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.visitRepository = visitRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @Test
    void get_withContentSetMission_returnsFirstProgressPerContentAndTargetContentCount() throws Exception {
        MissionFixtures fixtures = createMissionFixtures(MissionConditionType.CONTENT_SET, null);
        MissionParticipation participation = saveParticipation(fixtures.mission(), fixtures.visitor());
        Visit firstVisit = saveVisit(fixtures, fixtures.firstContent(), "content-set-first", FIRST_RECORDED_AT);
        Visit secondVisit = saveVisit(fixtures, fixtures.firstContent(), "content-set-second", SECOND_RECORDED_AT);
        missionProgressRepository.saveAndFlush(new MissionProgress(
            participation,
            firstVisit,
            fixtures.firstContent(),
            FIRST_RECORDED_AT
        ));
        missionProgressRepository.saveAndFlush(new MissionProgress(
            participation,
            secondVisit,
            fixtures.firstContent(),
            SECOND_RECORDED_AT
        ));
        entityManager.clear();

        mockMvc.perform(get("/api/v1/me/mission-participations/{participationId}",
                    participation.getMissionParticipationId())
                .header("Authorization", bearerToken(fixtures.visitor())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 미션 참여 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.participationId").value(participation.getMissionParticipationId().toString()))
            .andExpect(jsonPath("$.data.missionId").value(fixtures.mission().getMissionId().toString()))
            .andExpect(jsonPath("$.data.title").value("테스트 미션"))
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.conditionType").value("CONTENT_SET"))
            .andExpect(jsonPath("$.data.progressCount").value(1))
            .andExpect(jsonPath("$.data.requiredCount").value(2))
            .andExpect(jsonPath("$.data.rewardClaimed").value(false))
            .andExpect(jsonPath("$.data.joinedAt").value("2026-08-10T00:00:00Z"))
            .andExpect(jsonPath("$.data.completedAt").isEmpty())
            .andExpect(jsonPath("$.data.progresses.length()").value(1))
            .andExpect(jsonPath("$.data.progresses[0].visitId").value(firstVisit.getVisitId().toString()))
            .andExpect(jsonPath("$.data.progresses[0].contentId").value(fixtures.firstContent().getContentId().toString()))
            .andExpect(jsonPath("$.data.progresses[0].contentTitle").value(fixtures.firstContent().getTitle()))
            .andExpect(jsonPath("$.data.progresses[0].recordedAt").value("2026-08-11T00:00:00Z"));
    }

    @Test
    void get_withVisitCountMission_returnsProgressPerVisitAndRequiredVisitCount() throws Exception {
        MissionFixtures fixtures = createMissionFixtures(MissionConditionType.VISIT_COUNT, 3);
        MissionParticipation participation = saveParticipation(fixtures.mission(), fixtures.visitor());
        Visit firstVisit = saveVisit(fixtures, fixtures.firstContent(), "visit-count-first", FIRST_RECORDED_AT);
        Visit secondVisit = saveVisit(fixtures, fixtures.firstContent(), "visit-count-second", SECOND_RECORDED_AT);
        missionProgressRepository.saveAndFlush(new MissionProgress(
            participation,
            firstVisit,
            fixtures.firstContent(),
            FIRST_RECORDED_AT
        ));
        missionProgressRepository.saveAndFlush(new MissionProgress(
            participation,
            secondVisit,
            fixtures.firstContent(),
            SECOND_RECORDED_AT
        ));
        entityManager.clear();

        mockMvc.perform(get("/api/v1/me/mission-participations/{participationId}",
                    participation.getMissionParticipationId())
                .header("Authorization", bearerToken(fixtures.visitor())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.conditionType").value("VISIT_COUNT"))
            .andExpect(jsonPath("$.data.progressCount").value(2))
            .andExpect(jsonPath("$.data.requiredCount").value(3))
            .andExpect(jsonPath("$.data.progresses.length()").value(2))
            .andExpect(jsonPath("$.data.progresses[0].visitId").value(firstVisit.getVisitId().toString()))
            .andExpect(jsonPath("$.data.progresses[1].visitId").value(secondVisit.getVisitId().toString()));
    }

    @Test
    void get_withoutProgress_returnsEmptyProgressesAndZeroProgressCount() throws Exception {
        MissionFixtures fixtures = createMissionFixtures(MissionConditionType.VISIT_COUNT, 2);
        MissionParticipation participation = saveParticipation(fixtures.mission(), fixtures.visitor());
        entityManager.clear();

        mockMvc.perform(get("/api/v1/me/mission-participations/{participationId}",
                    participation.getMissionParticipationId())
                .header("Authorization", bearerToken(fixtures.visitor())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.progressCount").value(0))
            .andExpect(jsonPath("$.data.requiredCount").value(2))
            .andExpect(jsonPath("$.data.progresses").isEmpty());
    }

    @Test
    void get_withRewardClaim_returnsRewardClaimedTrueAndDoesNotChangeParticipationState() throws Exception {
        MissionFixtures fixtures = createMissionFixtures(MissionConditionType.VISIT_COUNT, 1);
        MissionParticipation participation = saveParticipation(fixtures.mission(), fixtures.visitor());
        participation.complete(COMPLETED_AT);
        missionParticipationRepository.flush();
        missionRewardClaimRepository.saveAndFlush(new MissionRewardClaim(
            participation,
            fixtures.rewardCouponPolicy(),
            COMPLETED_AT
        ));
        entityManager.clear();

        mockMvc.perform(get("/api/v1/me/mission-participations/{participationId}",
                    participation.getMissionParticipationId())
                .header("Authorization", bearerToken(fixtures.visitor())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.rewardClaimed").value(true))
            .andExpect(jsonPath("$.data.completedAt").value("2026-08-13T00:00:00Z"));

        MissionParticipation foundParticipation = missionParticipationRepository
            .findById(participation.getMissionParticipationId())
            .orElseThrow();
        assertThat(foundParticipation.getStatus()).isEqualTo(MissionParticipationStatus.COMPLETED);
        assertThat(foundParticipation.getCompletedAt()).isEqualTo(COMPLETED_AT);
    }

    @Test
    void get_withOtherVisitorParticipation_returnsForbidden() throws Exception {
        MissionFixtures fixtures = createMissionFixtures(MissionConditionType.VISIT_COUNT, 1);
        MissionParticipation participation = saveParticipation(fixtures.mission(), fixtures.visitor());
        AppUser otherVisitor = saveVisitor("other-visitor", AppUserStatus.ACTIVE);
        entityManager.clear();

        mockMvc.perform(get("/api/v1/me/mission-participations/{participationId}",
                    participation.getMissionParticipationId())
                .header("Authorization", bearerToken(otherVisitor)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void get_withUnlinkedParticipationUser_returnsForbidden() throws Exception {
        MissionFixtures fixtures = createMissionFixtures(MissionConditionType.VISIT_COUNT, 1);
        MissionParticipation participation = saveParticipation(fixtures.mission(), fixtures.visitor());
        AppUser otherVisitor = saveVisitor("unlinked-other-visitor", AppUserStatus.ACTIVE);
        entityManager.createNativeQuery("""
                UPDATE mission_participation
                SET user_id = NULL
                WHERE mission_participation_id = :participationId
                """)
            .setParameter("participationId", participation.getMissionParticipationId())
            .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/me/mission-participations/{participationId}",
                    participation.getMissionParticipationId())
                .header("Authorization", bearerToken(otherVisitor)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void get_withoutActiveVisitorRole_returnsForbidden() throws Exception {
        MissionFixtures fixtures = createMissionFixtures(MissionConditionType.VISIT_COUNT, 1);
        MissionParticipation participation = saveParticipation(fixtures.mission(), fixtures.visitor());
        AppUser operatorOnlyUser = saveUser("operator-only", AppUserStatus.ACTIVE);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            operatorOnlyUser,
            UserRole.OPERATOR,
            fixtures.region()
        ));
        entityManager.clear();

        mockMvc.perform(get("/api/v1/me/mission-participations/{participationId}",
                    participation.getMissionParticipationId())
                .header("Authorization", bearerToken(operatorOnlyUser)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void get_withMissingParticipation_returnsNotFound() throws Exception {
        AppUser visitor = saveVisitor("missing-visitor", AppUserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/me/mission-participations/999999999")
                .header("Authorization", bearerToken(visitor)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private MissionFixtures createMissionFixtures(
        MissionConditionType conditionType,
        Integer requiredVisitCount
    ) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "테스트 지역", true));
        AppUser operator = saveUser("operator-" + suffix, AppUserStatus.ACTIVE);
        AppUser visitor = saveVisitor("visitor-" + suffix, AppUserStatus.ACTIVE);
        Content rewardContent = saveContent(region, operator, "reward-" + suffix);
        Content firstContent = saveContent(region, operator, "first-" + suffix);
        Content secondContent = saveContent(region, operator, "second-" + suffix);
        CouponPolicy rewardCouponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
            rewardContent,
            region,
            "미션 보상 쿠폰",
            "미션 완료 보상 쿠폰입니다.",
            CouponIssuanceType.MISSION_REWARD,
            3_000,
            10_000,
            30,
            CONTENT_PUBLISHED_AT,
            COUPON_ISSUE_ENDS_AT,
            100L
        ));
        Mission mission = new Mission(
            "테스트 미션",
            region,
            conditionType,
            requiredVisitCount,
            rewardCouponPolicy,
            MISSION_ENDS_AT
        );
        if (conditionType == MissionConditionType.CONTENT_SET) {
            mission.addTargetContent(firstContent);
            mission.addTargetContent(secondContent);
        }
        Mission savedMission = missionRepository.saveAndFlush(mission);

        return new MissionFixtures(
            region,
            operator,
            visitor,
            rewardCouponPolicy,
            firstContent,
            secondContent,
            savedMission
        );
    }

    private MissionParticipation saveParticipation(
        Mission mission,
        AppUser visitor
    ) {
        return missionParticipationRepository.saveAndFlush(new MissionParticipation(
            mission,
            visitor,
            JOINED_AT
        ));
    }

    private Visit saveVisit(
        MissionFixtures fixtures,
        Content content,
        String suffix,
        Instant recordedAt
    ) {
        AppUser reviewer = saveUser("reviewer-" + suffix, AppUserStatus.ACTIVE);
        ContentSession contentSession = new ContentSession(
            content,
            fixtures.region(),
            recordedAt.plusSeconds(3600),
            recordedAt.plusSeconds(7200),
            recordedAt,
            recordedAt.plusSeconds(5400),
            10
        );
        contentSession.approve(reviewer, CONTENT_PUBLISHED_AT);
        ContentSession savedContentSession = contentSessionRepository.saveAndFlush(contentSession);
        CapacityHold capacityHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            fixtures.region(),
            savedContentSession,
            fixtures.visitor(),
            1,
            CapacityHoldStatus.CONSUMED,
            CONTENT_PUBLISHED_AT,
            CONTENT_PUBLISHED_AT,
            null,
            null
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-" + suffix,
            "QR-" + suffix,
            fixtures.region(),
            capacityHold,
            savedContentSession,
            fixtures.visitor(),
            ReservationStatus.CONFIRMED,
            CONTENT_PUBLISHED_AT,
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
            recordedAt
        ));
    }

    private Content saveContent(
        Region region,
        AppUser operator,
        String suffix
    ) {
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            suffix + " 콘텐츠",
            "미션 테스트를 위한 콘텐츠 설명입니다.",
            "김해시",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안전 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            CONTENT_PUBLISHED_AT
        ));
    }

    private AppUser saveVisitor(
        String prefix,
        AppUserStatus status
    ) {
        AppUser visitor = saveUser(prefix, status);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
        return visitor;
    }

    private AppUser saveUser(
        String prefix,
        AppUserStatus status
    ) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + "-" + suffix + "@example.com",
            "hashed-password",
            "테스트 사용자",
            "010-1234-5678",
            status
        ));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, user.getUserId());
    }

    private record MissionFixtures(
        Region region,
        AppUser operator,
        AppUser visitor,
        CouponPolicy rewardCouponPolicy,
        Content firstContent,
        Content secondContent,
        Mission mission
    ) {
    }
}
