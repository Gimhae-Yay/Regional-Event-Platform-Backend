package io.regionevent.regioneventbackend.domain.mission.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
import io.regionevent.regioneventbackend.domain.mission.service.CreateOperatorMissionResult;
import io.regionevent.regioneventbackend.domain.mission.service.CreateOperatorMissionUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.CreateOperatorMissionUseCase.CreateOperatorMissionCommand;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperatorMissionControllerIntegrationTest {

    private static final Instant CONTENT_PUBLISHED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant COUPON_ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant MISSION_ENDS_AT = Instant.parse("2026-09-30T14:59:59Z");
    private static final Instant MISSION_PUBLISHED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant MISSION_ENDED_AT = Instant.parse("2026-09-01T00:00:00Z");

    private final MockMvc mockMvc;
    private final MissionRepository missionRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final CreateOperatorMissionUseCase createOperatorMissionUseCase;
    private final EntityManager entityManager;

    @Autowired
    OperatorMissionControllerIntegrationTest(
        MockMvc mockMvc,
        MissionRepository missionRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        AuditEventRepository auditEventRepository,
        JwtAccessTokenService jwtAccessTokenService,
        CreateOperatorMissionUseCase createOperatorMissionUseCase,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.missionRepository = missionRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.auditEventRepository = auditEventRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.createOperatorMissionUseCase = createOperatorMissionUseCase;
        this.entityManager = entityManager;
    }

    @Test
    void create_withContentSetMission_persistsDraftMissionTargetContentsAndSuccessAudit() {
        Region region = saveRegion("C");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Content rewardContent = saveContent(region, operator, "reward-create");
        Content firstTargetContent = saveContent(region, operator, "first-create");
        Content secondTargetContent = saveContent(region, operator, "second-create");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, region);

        CreateOperatorMissionResult result = createOperatorMissionUseCase.create(
            operator.getUserId(),
            new CreateOperatorMissionCommand(
                "CONTENT_SET",
                null,
                List.of(secondTargetContent.getContentId(), firstTargetContent.getContentId()),
                rewardCouponPolicy.getCouponPolicyId(),
                OffsetDateTime.parse("2027-09-30T23:59:59+09:00")
            ),
            UUID.fromString("00000000-0000-0000-0000-000000000628")
        );

        Mission mission = missionRepository.findById(result.missionId()).orElseThrow();
        assertThat(mission.getStatus()).isEqualTo(io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus.DRAFT);
        assertThat(mission.getTargetContents())
            .extracting(targetContent -> targetContent.getContent().getContentId())
            .containsExactlyInAnyOrder(firstTargetContent.getContentId(), secondTargetContent.getContentId());
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.MISSION);
            assertThat(auditEvent.getTargetId()).isEqualTo(mission.getMissionId());
            assertThat(auditEvent.getPreviousState()).isNull();
            assertThat(auditEvent.getNextState()).isEqualTo("DRAFT");
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getReasonCode()).isEqualTo("MISSION_CREATED");
        });
    }

    @Test
    void create_withVisitCountMission_persistsDraftMissionAndSuccessAudit() {
        Region region = saveRegion("VC");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Content rewardContent = saveContent(region, operator, "reward-visit-count");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, region);

        CreateOperatorMissionResult result = createOperatorMissionUseCase.create(
            operator.getUserId(),
            new CreateOperatorMissionCommand(
                "VISIT_COUNT",
                3,
                List.of(),
                rewardCouponPolicy.getCouponPolicyId(),
                OffsetDateTime.parse("2027-09-30T23:59:59+09:00")
            ),
            UUID.fromString("00000000-0000-0000-0000-000000000629")
        );

        Mission mission = missionRepository.findById(result.missionId()).orElseThrow();
        assertThat(mission.getStatus()).isEqualTo(io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus.DRAFT);
        assertThat(mission.getConditionType()).isEqualTo(MissionConditionType.VISIT_COUNT);
        assertThat(mission.getRequiredVisitCount()).isEqualTo(3);
        assertThat(mission.getTargetContents()).isEmpty();
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.MISSION);
            assertThat(auditEvent.getTargetId()).isEqualTo(mission.getMissionId());
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getReasonCode()).isEqualTo("MISSION_CREATED");
        });
    }

    @Test
    void getDetail_withContentSetMission_returnsNullableVisitCountAndOrderedTargetContents() throws Exception {
        Region region = saveRegion("G");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Content rewardContent = saveContent(region, operator, "reward");
        Content firstTargetContent = saveContent(region, operator, "first-target");
        Content secondTargetContent = saveContent(region, operator, "second-target");
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
        mission = missionRepository.saveAndFlush(mission);
        entityManager.clear();

        mockMvc.perform(get("/api/v1/operator/missions/{missionId}", mission.getMissionId())
                .header("Authorization", bearerToken(operator)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 미션 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.missionId").value(mission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.regionId").value(region.getRegionId().toString()))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.conditionType").value("CONTENT_SET"))
            .andExpect(jsonPath("$.data.requiredVisitCount").isEmpty())
            .andExpect(jsonPath("$.data.targetContents[0].contentId").value(firstTargetContent.getContentId().toString()))
            .andExpect(jsonPath("$.data.targetContents[0].title").value(firstTargetContent.getTitle()))
            .andExpect(jsonPath("$.data.targetContents[1].contentId").value(secondTargetContent.getContentId().toString()))
            .andExpect(jsonPath("$.data.rewardCouponPolicyId").value(rewardCouponPolicy.getCouponPolicyId().toString()))
            .andExpect(jsonPath("$.data.endsAt").value("2026-09-30T23:59:59+09:00"))
            .andExpect(jsonPath("$.data.publishedAt").isEmpty())
            .andExpect(jsonPath("$.data.endedAt").isEmpty());
    }

    @Test
    void getDetail_withVisitCountMission_returnsRequiredVisitCountAndEmptyTargetContents() throws Exception {
        Region region = saveRegion("V");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Content rewardContent = saveContent(region, operator, "reward");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, region);
        Mission mission = missionRepository.saveAndFlush(new Mission(
            region,
            MissionConditionType.VISIT_COUNT,
            3,
            rewardCouponPolicy,
            MISSION_ENDS_AT
        ));
        publishAndEnd(mission);
        entityManager.clear();

        mockMvc.perform(get("/api/v1/operator/missions/{missionId}", mission.getMissionId())
                .header("Authorization", bearerToken(operator)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ENDED"))
            .andExpect(jsonPath("$.data.conditionType").value("VISIT_COUNT"))
            .andExpect(jsonPath("$.data.requiredVisitCount").value(3))
            .andExpect(jsonPath("$.data.targetContents").isEmpty())
            .andExpect(jsonPath("$.data.endsAt").value("2026-09-30T23:59:59+09:00"))
            .andExpect(jsonPath("$.data.publishedAt").value("2026-08-10T00:00:00Z"))
            .andExpect(jsonPath("$.data.endedAt").value("2026-09-01T00:00:00Z"));
    }

    @Test
    void getDetail_withOtherRegionOperator_returnsForbidden() throws Exception {
        Region region = saveRegion("A");
        Region otherRegion = saveRegion("B");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        AppUser otherRegionOperator = saveOperator(otherRegion, AppUserStatus.ACTIVE);
        Mission mission = saveVisitCountMission(region, operator);

        mockMvc.perform(get("/api/v1/operator/missions/{missionId}", mission.getMissionId())
                .header("Authorization", bearerToken(otherRegionOperator)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getDetail_withoutOperatorRole_returnsForbidden() throws Exception {
        Region region = saveRegion("N");
        AppUser user = saveUser("visitor", AppUserStatus.ACTIVE);
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Mission mission = saveVisitCountMission(region, operator);

        mockMvc.perform(get("/api/v1/operator/missions/{missionId}", mission.getMissionId())
                .header("Authorization", bearerToken(user)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getDetail_withMissingMission_returnsNotFound() throws Exception {
        Region region = saveRegion("M");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/operator/missions/999999999")
                .header("Authorization", bearerToken(operator)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getDetail_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/operator/missions/1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private Mission saveVisitCountMission(
        Region region,
        AppUser operator
    ) {
        Content rewardContent = saveContent(region, operator, "reward");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, region);
        return missionRepository.saveAndFlush(new Mission(
            region,
            MissionConditionType.VISIT_COUNT,
            1,
            rewardCouponPolicy,
            MISSION_ENDS_AT
        ));
    }

    private void publishAndEnd(Mission mission) {
        entityManager.createNativeQuery("""
            UPDATE mission
            SET status = 'ENDED',
                published_at = :publishedAt,
                ended_at = :endedAt
            WHERE mission_id = :missionId
            """)
            .setParameter("publishedAt", MISSION_PUBLISHED_AT)
            .setParameter("endedAt", MISSION_ENDED_AT)
            .setParameter("missionId", mission.getMissionId())
            .executeUpdate();
    }

    private Region saveRegion(String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return regionRepository.saveAndFlush(new Region(prefix + suffix, "테스트 지역", true));
    }

    private AppUser saveOperator(
        Region region,
        AppUserStatus status
    ) {
        AppUser operator = saveUser("operator", status);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        return operator;
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

    private CouponPolicy saveMissionRewardCouponPolicy(
        Content content,
        Region region
    ) {
        return couponPolicyRepository.saveAndFlush(new CouponPolicy(
            content,
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
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }
}
