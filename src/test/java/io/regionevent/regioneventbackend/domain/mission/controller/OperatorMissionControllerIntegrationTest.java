package io.regionevent.regioneventbackend.domain.mission.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
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
        this.entityManager = entityManager;
    }

    @Test
    void create_withContentSetMissionRequest_persistsDraftMissionTargetContentsAndSuccessAudit() throws Exception {
        Region region = saveRegion("C");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Content rewardContent = saveContent(region, operator, "reward-create");
        Content firstTargetContent = saveContent(region, operator, "first-create");
        Content secondTargetContent = saveContent(region, operator, "second-create");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, region);

        mockMvc.perform(post("/api/v1/operator/missions")
                .header("Authorization", bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionRequest(
                    "CONTENT_SET",
                    null,
                    List.of(secondTargetContent.getContentId(), firstTargetContent.getContentId()),
                    rewardCouponPolicy.getCouponPolicyId()
                )))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));

        Mission mission = missionRepository.findAll().stream().findFirst().orElseThrow();
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
    void create_withVisitCountMissionRequest_persistsDraftMissionAndSuccessAudit() throws Exception {
        Region region = saveRegion("VC");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Content rewardContent = saveContent(region, operator, "reward-visit-count");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, region);

        mockMvc.perform(post("/api/v1/operator/missions")
                .header("Authorization", bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionRequest(
                    "VISIT_COUNT",
                    3,
                    List.of(),
                    rewardCouponPolicy.getCouponPolicyId()
                )))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));

        Mission mission = missionRepository.findAll().stream().findFirst().orElseThrow();
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
    void update_withDraftMission_replacesCoreValuesTargetsAndRecordsSuccessAudit() throws Exception {
        Region region = saveRegion("UPDATE");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Content currentRewardContent = saveContent(region, operator, "update-current-reward");
        Content requestedRewardContent = saveContent(region, operator, "update-requested-reward");
        Content previousTarget = saveContent(region, operator, "update-previous-target");
        Content requestedTarget = saveContent(
            region,
            operator,
            "update-requested-target",
            ContentStatus.PENDING
        );
        CouponPolicy currentPolicy = saveMissionRewardCouponPolicy(currentRewardContent, region);
        CouponPolicy requestedPolicy = saveMissionRewardCouponPolicy(requestedRewardContent, region);
        Mission mission = new Mission(
            region,
            MissionConditionType.CONTENT_SET,
            null,
            currentPolicy,
            MISSION_ENDS_AT
        );
        mission.addTargetContent(previousTarget);
        mission = missionRepository.saveAndFlush(mission);
        Long missionId = mission.getMissionId();

        mockMvc.perform(patch("/api/v1/operator/missions/{missionId}", missionId)
                .header("Authorization", bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionRequest(
                    "CONTENT_SET",
                    null,
                    List.of(requestedTarget.getContentId()),
                    requestedPolicy.getCouponPolicyId()
                )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("미션 수정에 성공했습니다."))
            .andExpect(jsonPath("$.data.missionId").value(missionId.toString()))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));

        entityManager.flush();
        entityManager.clear();
        Mission updatedMission = missionRepository.findMissionDetailByMissionId(missionId).orElseThrow();
        assertThat(updatedMission.getConditionType()).isEqualTo(MissionConditionType.CONTENT_SET);
        assertThat(updatedMission.getRequiredVisitCount()).isNull();
        assertThat(updatedMission.getRewardCouponPolicy().getCouponPolicyId())
            .isEqualTo(requestedPolicy.getCouponPolicyId());
        assertThat(updatedMission.getEndsAt()).isEqualTo(Instant.parse("2027-09-30T14:59:59Z"));
        assertThat(updatedMission.getTargetContents())
            .extracting(targetContent -> targetContent.getContent().getContentId())
            .containsExactly(requestedTarget.getContentId());
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.MISSION);
            assertThat(auditEvent.getTargetId()).isEqualTo(missionId);
            assertThat(auditEvent.getPreviousState()).isEqualTo("DRAFT");
            assertThat(auditEvent.getNextState()).isEqualTo("DRAFT");
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getReasonCode()).isEqualTo("MISSION_UPDATED");
        });
    }

    @Test
    void update_withNonDraftMission_returnsConflictWithoutChangingMission() throws Exception {
        Region region = saveRegion("UPDATE-CONFLICT");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Mission mission = saveVisitCountMission(region, operator);
        Long originalPolicyId = mission.getRewardCouponPolicy().getCouponPolicyId();
        setMissionStatus(mission, MissionStatus.PENDING_REVIEW);

        mockMvc.perform(patch("/api/v1/operator/missions/{missionId}", mission.getMissionId())
                .header("Authorization", bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionRequest("VISIT_COUNT", 4, List.of(), originalPolicyId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MISSION_STATE_CONFLICT"));

        Mission unchangedMission = missionRepository.findByMissionId(mission.getMissionId()).orElseThrow();
        assertThat(unchangedMission.getStatus()).isEqualTo(MissionStatus.PENDING_REVIEW);
        assertThat(unchangedMission.getRequiredVisitCount()).isEqualTo(1);
        assertThat(unchangedMission.getRewardCouponPolicy().getCouponPolicyId()).isEqualTo(originalPolicyId);
    }

    @Test
    void update_withOtherRegionOperator_returnsForbiddenWithoutChangingMission() throws Exception {
        Region missionRegion = saveRegion("UPDATE-MISSION");
        Region otherRegion = saveRegion("UPDATE-OTHER");
        AppUser missionOperator = saveOperator(missionRegion, AppUserStatus.ACTIVE);
        AppUser otherOperator = saveOperator(otherRegion, AppUserStatus.ACTIVE);
        Mission mission = saveVisitCountMission(missionRegion, missionOperator);
        Long originalPolicyId = mission.getRewardCouponPolicy().getCouponPolicyId();

        mockMvc.perform(patch("/api/v1/operator/missions/{missionId}", mission.getMissionId())
                .header("Authorization", bearerToken(otherOperator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionRequest("VISIT_COUNT", 4, List.of(), originalPolicyId)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        Mission unchangedMission = missionRepository.findByMissionId(mission.getMissionId()).orElseThrow();
        assertThat(unchangedMission.getConditionType()).isEqualTo(MissionConditionType.VISIT_COUNT);
        assertThat(unchangedMission.getRequiredVisitCount()).isEqualTo(1);
        assertThat(unchangedMission.getRewardCouponPolicy().getCouponPolicyId()).isEqualTo(originalPolicyId);
    }

    @Test
    void update_withEndedRewardPolicy_returnsConflictWithoutChangingMission() throws Exception {
        Region region = saveRegion("UPDATE-POLICY");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Mission mission = saveVisitCountMission(region, operator);
        Long originalPolicyId = mission.getRewardCouponPolicy().getCouponPolicyId();
        Content endedRewardContent = saveContent(region, operator, "update-ended-policy");
        CouponPolicy endedPolicy = saveMissionRewardCouponPolicy(endedRewardContent, region);
        endedPolicy.publish(CONTENT_PUBLISHED_AT);
        endedPolicy.end(COUPON_ISSUE_ENDS_AT);
        couponPolicyRepository.saveAndFlush(endedPolicy);

        mockMvc.perform(patch("/api/v1/operator/missions/{missionId}", mission.getMissionId())
                .header("Authorization", bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionRequest(
                    "VISIT_COUNT",
                    4,
                    List.of(),
                    endedPolicy.getCouponPolicyId()
                )))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MISSION_STATE_CONFLICT"));

        Mission unchangedMission = missionRepository.findByMissionId(mission.getMissionId()).orElseThrow();
        assertThat(unchangedMission.getConditionType()).isEqualTo(MissionConditionType.VISIT_COUNT);
        assertThat(unchangedMission.getRequiredVisitCount()).isEqualTo(1);
        assertThat(unchangedMission.getRewardCouponPolicy().getCouponPolicyId()).isEqualTo(originalPolicyId);
    }

    @Test
    void update_withDeletedTargetContent_returnsNotFoundWithoutChangingMission() throws Exception {
        Region region = saveRegion("UPDATE-CONTENT");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Mission mission = saveVisitCountMission(region, operator);
        Long originalPolicyId = mission.getRewardCouponPolicy().getCouponPolicyId();
        Content deletedTarget = saveContent(region, operator, "update-deleted-target", ContentStatus.APPROVED);
        deletedTarget.softDelete(CONTENT_PUBLISHED_AT);
        contentRepository.saveAndFlush(deletedTarget);

        mockMvc.perform(patch("/api/v1/operator/missions/{missionId}", mission.getMissionId())
                .header("Authorization", bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionRequest(
                    "CONTENT_SET",
                    null,
                    List.of(deletedTarget.getContentId()),
                    originalPolicyId
                )))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        Mission unchangedMission = missionRepository.findMissionDetailByMissionId(mission.getMissionId())
            .orElseThrow();
        assertThat(unchangedMission.getConditionType()).isEqualTo(MissionConditionType.VISIT_COUNT);
        assertThat(unchangedMission.getRequiredVisitCount()).isEqualTo(1);
        assertThat(unchangedMission.getRewardCouponPolicy().getCouponPolicyId()).isEqualTo(originalPolicyId);
        assertThat(unchangedMission.getTargetContents()).isEmpty();
    }

    @Test
    void submit_withDraftMission_changesStatusAndRecordsSuccessAudit() throws Exception {
        Region region = saveRegion("SUB");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Mission mission = saveVisitCountMission(region, operator);

        mockMvc.perform(post("/api/v1/operator/missions/{missionId}/submit", mission.getMissionId())
                .header("Authorization", bearerToken(operator)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("미션 검토 요청에 성공했습니다."))
            .andExpect(jsonPath("$.data.missionId").value(mission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));

        entityManager.clear();
        assertThat(missionRepository.findById(mission.getMissionId()))
            .hasValueSatisfying(savedMission ->
                assertThat(savedMission.getStatus()).isEqualTo(MissionStatus.PENDING_REVIEW)
            );
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.MISSION);
            assertThat(auditEvent.getTargetId()).isEqualTo(mission.getMissionId());
            assertThat(auditEvent.getPreviousState()).isEqualTo(MissionStatus.DRAFT.name());
            assertThat(auditEvent.getNextState()).isEqualTo(MissionStatus.PENDING_REVIEW.name());
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getReasonCode()).isEqualTo("MISSION_SUBMITTED");
            assertThat(auditEvent.getReason()).isNull();
        });
    }

    @Test
    void submit_withoutOperatorRole_returnsForbiddenWithoutChangingMissionOrAudit() throws Exception {
        Region region = saveRegion("SUB-NO-ROLE");
        AppUser owner = saveOperator(region, AppUserStatus.ACTIVE);
        AppUser user = saveUser("user", AppUserStatus.ACTIVE);
        Mission mission = saveVisitCountMission(region, owner);

        mockMvc.perform(post("/api/v1/operator/missions/{missionId}/submit", mission.getMissionId())
                .header("Authorization", bearerToken(user)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(missionRepository.findById(mission.getMissionId()))
            .hasValueSatisfying(savedMission -> assertThat(savedMission.getStatus()).isEqualTo(MissionStatus.DRAFT));
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void submit_withOtherRegionOperator_returnsForbiddenWithoutChangingMissionOrAudit() throws Exception {
        Region missionRegion = saveRegion("SUB-MISSION");
        Region otherRegion = saveRegion("SUB-OTHER");
        AppUser missionOperator = saveOperator(missionRegion, AppUserStatus.ACTIVE);
        AppUser otherOperator = saveOperator(otherRegion, AppUserStatus.ACTIVE);
        Mission mission = saveVisitCountMission(missionRegion, missionOperator);

        mockMvc.perform(post("/api/v1/operator/missions/{missionId}/submit", mission.getMissionId())
                .header("Authorization", bearerToken(otherOperator)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(missionRepository.findById(mission.getMissionId()))
            .hasValueSatisfying(savedMission -> assertThat(savedMission.getStatus()).isEqualTo(MissionStatus.DRAFT));
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void submit_withMissingMission_returnsNotFound() throws Exception {
        Region region = saveRegion("SUB-MISSING");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/operator/missions/999999/submit")
                .header("Authorization", bearerToken(operator)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void submit_withNonDraftMission_returnsConflictWithoutRecordingAudit() throws Exception {
        Region region = saveRegion("SUB-STATE");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Mission mission = saveVisitCountMission(region, operator);
        setMissionStatus(mission, MissionStatus.PENDING_REVIEW);

        mockMvc.perform(post("/api/v1/operator/missions/{missionId}/submit", mission.getMissionId())
                .header("Authorization", bearerToken(operator)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MISSION_STATE_CONFLICT"));

        assertThat(missionRepository.findById(mission.getMissionId()))
            .hasValueSatisfying(savedMission ->
                assertThat(savedMission.getStatus()).isEqualTo(MissionStatus.PENDING_REVIEW)
            );
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void submit_withInvalidRewardCouponPolicy_returnsConflictWithoutChangingMissionOrAudit() throws Exception {
        Region region = saveRegion("SUB-POLICY");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Mission mission = saveVisitCountMission(region, operator);
        entityManager.createNativeQuery("""
            UPDATE coupon_policy
            SET issuance_type = 'VISIT'
            WHERE coupon_policy_id = :couponPolicyId
            """)
            .setParameter("couponPolicyId", mission.getRewardCouponPolicy().getCouponPolicyId())
            .executeUpdate();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/operator/missions/{missionId}/submit", mission.getMissionId())
                .header("Authorization", bearerToken(operator)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MISSION_STATE_CONFLICT"));

        assertThat(missionRepository.findById(mission.getMissionId()))
            .hasValueSatisfying(savedMission -> assertThat(savedMission.getStatus()).isEqualTo(MissionStatus.DRAFT));
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void submit_withEndedRewardCouponPolicy_returnsConflictWithoutChangingMissionOrAudit() throws Exception {
        Region region = saveRegion("SUB-ENDED-POLICY");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Mission mission = saveVisitCountMission(region, operator);
        entityManager.createNativeQuery("""
            UPDATE coupon_policy
            SET status = 'ENDED',
                published_at = :publishedAt,
                ended_at = :endedAt
            WHERE coupon_policy_id = :couponPolicyId
            """)
            .setParameter("publishedAt", CONTENT_PUBLISHED_AT)
            .setParameter("endedAt", CONTENT_PUBLISHED_AT.plusSeconds(1))
            .setParameter("couponPolicyId", mission.getRewardCouponPolicy().getCouponPolicyId())
            .executeUpdate();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/operator/missions/{missionId}/submit", mission.getMissionId())
                .header("Authorization", bearerToken(operator)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MISSION_STATE_CONFLICT"));

        assertThat(missionRepository.findById(mission.getMissionId()))
            .hasValueSatisfying(savedMission -> assertThat(savedMission.getStatus()).isEqualTo(MissionStatus.DRAFT));
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void submit_withDifferentRegionRewardCouponPolicy_returnsForbiddenWithoutChangingMissionOrAudit()
        throws Exception {
        Region missionRegion = saveRegion("SUB-POLICY-MISSION");
        Region policyRegion = saveRegion("SUB-POLICY-OTHER");
        AppUser missionOperator = saveOperator(missionRegion, AppUserStatus.ACTIVE);
        AppUser policyOperator = saveOperator(policyRegion, AppUserStatus.ACTIVE);
        Mission mission = saveVisitCountMission(missionRegion, missionOperator);
        Content policyContent = saveContent(policyRegion, policyOperator, "different-region-policy");
        CouponPolicy differentRegionPolicy = saveMissionRewardCouponPolicy(policyContent, policyRegion);
        entityManager.createNativeQuery("""
            UPDATE mission
            SET reward_coupon_policy_id = :couponPolicyId
            WHERE mission_id = :missionId
            """)
            .setParameter("couponPolicyId", differentRegionPolicy.getCouponPolicyId())
            .setParameter("missionId", mission.getMissionId())
            .executeUpdate();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/operator/missions/{missionId}/submit", mission.getMissionId())
                .header("Authorization", bearerToken(missionOperator)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(missionRepository.findById(mission.getMissionId()))
            .hasValueSatisfying(savedMission -> assertThat(savedMission.getStatus()).isEqualTo(MissionStatus.DRAFT));
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void create_withNonMissionRewardPolicy_returnsMissionStateConflict() throws Exception {
        Region region = saveRegion("IP");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Content rewardContent = saveContent(region, operator, "invalid-policy-reward");
        CouponPolicy rewardCouponPolicy = saveCouponPolicy(rewardContent, region, CouponIssuanceType.VISIT);

        mockMvc.perform(post("/api/v1/operator/missions")
                .header("Authorization", bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionRequest(
                    "VISIT_COUNT",
                    3,
                    List.of(),
                    rewardCouponPolicy.getCouponPolicyId()
                )))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MISSION_STATE_CONFLICT"));

        assertMissionWasNotCreated();
    }

    @Test
    void create_withEndedRewardCouponPolicy_returnsMissionStateConflict() throws Exception {
        Region region = saveRegion("EP");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Content rewardContent = saveContent(region, operator, "ended-policy-reward");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, region);
        rewardCouponPolicy.publish(CONTENT_PUBLISHED_AT);
        rewardCouponPolicy.end(COUPON_ISSUE_ENDS_AT);
        couponPolicyRepository.saveAndFlush(rewardCouponPolicy);

        mockMvc.perform(post("/api/v1/operator/missions")
                .header("Authorization", bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionRequest(
                    "VISIT_COUNT",
                    3,
                    List.of(),
                    rewardCouponPolicy.getCouponPolicyId()
                )))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MISSION_STATE_CONFLICT"));

        assertMissionWasNotCreated();
    }

    @Test
    void create_withDeletedTargetContent_returnsNotFound() throws Exception {
        Region region = saveRegion("DT");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Content rewardContent = saveContent(region, operator, "deleted-target-reward");
        Content targetContent = saveContent(region, operator, "deleted-target", ContentStatus.APPROVED);
        targetContent.softDelete(CONTENT_PUBLISHED_AT);
        contentRepository.saveAndFlush(targetContent);
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, region);

        mockMvc.perform(post("/api/v1/operator/missions")
                .header("Authorization", bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionRequest(
                    "CONTENT_SET",
                    null,
                    List.of(targetContent.getContentId()),
                    rewardCouponPolicy.getCouponPolicyId()
                )))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertMissionWasNotCreated();
    }

    @Test
    void create_withDifferentRegionTargetContent_returnsForbidden() throws Exception {
        Region region = saveRegion("TR");
        Region otherRegion = saveRegion("OTR");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        AppUser otherRegionOperator = saveOperator(otherRegion, AppUserStatus.ACTIVE);
        Content rewardContent = saveContent(region, operator, "target-region-reward");
        Content otherRegionTargetContent = saveContent(otherRegion, otherRegionOperator, "other-target");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, region);

        mockMvc.perform(post("/api/v1/operator/missions")
                .header("Authorization", bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionRequest(
                    "CONTENT_SET",
                    null,
                    List.of(otherRegionTargetContent.getContentId()),
                    rewardCouponPolicy.getCouponPolicyId()
                )))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertMissionWasNotCreated();
    }

    @Test
    void create_withoutOperatorRole_returnsForbidden() throws Exception {
        Region region = saveRegion("ROLE");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        AppUser visitor = saveUser("visitor", AppUserStatus.ACTIVE);
        Content rewardContent = saveContent(region, operator, "role-reward");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, region);

        mockMvc.perform(post("/api/v1/operator/missions")
                .header("Authorization", bearerToken(visitor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionRequest(
                    "VISIT_COUNT",
                    3,
                    List.of(),
                    rewardCouponPolicy.getCouponPolicyId()
                )))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertMissionWasNotCreated();
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

    @Test
    void getMissions_returnsAllAssignedRegionStatusesIncludingOtherOperatorMissions() throws Exception {
        Region assignedRegion = saveRegion("LIST-ASSIGNED");
        Region otherRegion = saveRegion("LIST-OTHER");
        AppUser operator = saveOperator(assignedRegion, AppUserStatus.ACTIVE);
        AppUser sameRegionOtherOperator = saveOperator(assignedRegion, AppUserStatus.ACTIVE);
        AppUser otherRegionOperator = saveOperator(otherRegion, AppUserStatus.ACTIVE);
        Mission draftMission = saveVisitCountMission(assignedRegion, operator);
        Mission pendingReviewMission = saveVisitCountMission(assignedRegion, operator);
        Mission publishedOtherOperatorMission = saveContentSetMission(assignedRegion, sameRegionOtherOperator);
        Mission endedOtherOperatorMission = saveVisitCountMission(assignedRegion, sameRegionOtherOperator);
        Mission otherRegionMission = saveVisitCountMission(otherRegion, otherRegionOperator);
        setMissionStatus(pendingReviewMission, MissionStatus.PENDING_REVIEW);
        setMissionStatus(publishedOtherOperatorMission, MissionStatus.PUBLISHED);
        setMissionStatus(endedOtherOperatorMission, MissionStatus.ENDED);
        setMissionStatus(otherRegionMission, MissionStatus.PUBLISHED);

        mockMvc.perform(get("/api/v1/operator/missions")
                .header("Authorization", bearerToken(operator)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 미션 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.content.length()").value(4))
            .andExpect(jsonPath("$.data.content[0].missionId")
                .value(endedOtherOperatorMission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.content[0].status").value("ENDED"))
            .andExpect(jsonPath("$.data.content[0].conditionType").value("VISIT_COUNT"))
            .andExpect(jsonPath("$.data.content[0].endsAt").value("2026-09-30T23:59:59+09:00"))
            .andExpect(jsonPath("$.data.content[1].missionId")
                .value(publishedOtherOperatorMission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.content[1].status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.content[1].conditionType").value("CONTENT_SET"))
            .andExpect(jsonPath("$.data.content[2].missionId")
                .value(pendingReviewMission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.content[2].status").value("PENDING_REVIEW"))
            .andExpect(jsonPath("$.data.content[3].missionId").value(draftMission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.content[3].status").value("DRAFT"))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(4))
            .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    void getMissions_withStatus_returnsOnlyRequestedStatus() throws Exception {
        Region region = saveRegion("LIST-STATUS");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Mission draftMission = saveVisitCountMission(region, operator);
        Mission publishedMission = saveVisitCountMission(region, operator);
        setMissionStatus(publishedMission, MissionStatus.PUBLISHED);

        mockMvc.perform(get("/api/v1/operator/missions")
                .header("Authorization", bearerToken(operator))
                .param("status", "PUBLISHED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].missionId").value(publishedMission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.content[0].status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1));

        assertThat(draftMission.getMissionId()).isNotEqualTo(publishedMission.getMissionId());
    }

    @Test
    void getMissions_withPage_returnsMissionIdDescendingPage() throws Exception {
        Region region = saveRegion("LIST-PAGE");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);
        Mission firstMission = saveVisitCountMission(region, operator);
        Mission secondMission = saveVisitCountMission(region, operator);
        Mission thirdMission = saveVisitCountMission(region, operator);

        mockMvc.perform(get("/api/v1/operator/missions")
                .header("Authorization", bearerToken(operator))
                .param("page", "1")
                .param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].missionId").value(secondMission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(1))
            .andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(3));

        assertThat(thirdMission.getMissionId()).isGreaterThan(secondMission.getMissionId());
        assertThat(secondMission.getMissionId()).isGreaterThan(firstMission.getMissionId());
    }

    @Test
    void getMissions_withoutRegionMissions_returnsEmptyPage() throws Exception {
        Region region = saveRegion("LIST-EMPTY");
        AppUser operator = saveOperator(region, AppUserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/operator/missions")
                .header("Authorization", bearerToken(operator)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isEmpty())
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(0))
            .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    @Test
    void getMissions_withoutOperatorRole_returnsForbidden() throws Exception {
        AppUser user = saveUser("list-user", AppUserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/operator/missions")
                .header("Authorization", bearerToken(user)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getMissions_withRevokedOperatorAssignment_returnsForbidden() throws Exception {
        Region region = saveRegion("LIST-REVOKED");
        AppUser operator = saveUser("list-revoked", AppUserStatus.ACTIVE);
        UserRoleAssignment assignment = userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(operator, UserRole.OPERATOR, region)
        );
        assignment.revoke(Instant.parse("2026-08-11T00:00:00Z"), "TEST_REVOKED");
        userRoleAssignmentRepository.saveAndFlush(assignment);

        mockMvc.perform(get("/api/v1/operator/missions")
                .header("Authorization", bearerToken(operator)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
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

    private Mission saveContentSetMission(
        Region region,
        AppUser operator
    ) {
        Content rewardContent = saveContent(region, operator, "content-set-reward");
        Content targetContent = saveContent(region, operator, "content-set-target");
        CouponPolicy rewardCouponPolicy = saveMissionRewardCouponPolicy(rewardContent, region);
        Mission mission = new Mission(
            region,
            MissionConditionType.CONTENT_SET,
            null,
            rewardCouponPolicy,
            MISSION_ENDS_AT
        );
        mission.addTargetContent(targetContent);
        return missionRepository.saveAndFlush(mission);
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

    private void setMissionStatus(Mission mission, MissionStatus status) {
        switch (status) {
            case PUBLISHED -> entityManager.createNativeQuery("""
                UPDATE mission
                SET status = :status,
                    published_at = :publishedAt
                WHERE mission_id = :missionId
                """)
                .setParameter("status", status.name())
                .setParameter("publishedAt", MISSION_PUBLISHED_AT)
                .setParameter("missionId", mission.getMissionId())
                .executeUpdate();
            case ENDED -> entityManager.createNativeQuery("""
                UPDATE mission
                SET status = :status,
                    published_at = :publishedAt,
                    ended_at = :endedAt
                WHERE mission_id = :missionId
                """)
                .setParameter("status", status.name())
                .setParameter("publishedAt", MISSION_PUBLISHED_AT)
                .setParameter("endedAt", MISSION_ENDED_AT)
                .setParameter("missionId", mission.getMissionId())
                .executeUpdate();
            default -> entityManager.createNativeQuery("""
                UPDATE mission
                SET status = :status
                WHERE mission_id = :missionId
                """)
                .setParameter("status", status.name())
                .setParameter("missionId", mission.getMissionId())
                .executeUpdate();
        }
        entityManager.clear();
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
        return saveContent(region, operator, suffix, ContentStatus.PUBLISHED);
    }

    private Content saveContent(
        Region region,
        AppUser operator,
        String suffix,
        ContentStatus status
    ) {
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
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
        return saveCouponPolicy(content, region, CouponIssuanceType.MISSION_REWARD);
    }

    private CouponPolicy saveCouponPolicy(
        Content content,
        Region region,
        CouponIssuanceType issuanceType
    ) {
        return couponPolicyRepository.saveAndFlush(new CouponPolicy(
            content,
            region,
            "미션 보상 쿠폰",
            "미션 완료 보상 쿠폰입니다.",
            issuanceType,
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

    private void assertMissionWasNotCreated() {
        assertThat(missionRepository.count()).isZero();
        assertThat(auditEventRepository.count()).isZero();
    }

    private String createMissionRequest(
        String conditionType,
        Integer requiredVisitCount,
        List<Long> targetContentIds,
        Long rewardCouponPolicyId
    ) {
        String targetContentIdsJson = targetContentIds.stream()
            .map(contentId -> "\"%d\"".formatted(contentId))
            .collect(Collectors.joining(", "));
        return """
            {
              "conditionType": "%s",
              "requiredVisitCount": %s,
              "targetContentIds": [%s],
              "rewardCouponPolicyId": "%d",
              "endsAt": "2027-09-30T23:59:59+09:00"
            }
            """.formatted(
                conditionType,
                requiredVisitCount,
                targetContentIdsJson,
                rewardCouponPolicyId
            );
    }
}
