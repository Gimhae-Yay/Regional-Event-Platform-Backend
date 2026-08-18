package io.regionevent.regioneventbackend.domain.mission.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
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
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class ApproveRegionAdminMissionControllerIntegrationTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant FUTURE_ENDS_AT = Instant.parse("2099-09-30T14:59:59Z");
    private static final Instant EXPIRED_ENDS_AT = Instant.parse("2020-09-30T14:59:59Z");

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final MissionRepository missionRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    @Autowired
    ApproveRegionAdminMissionControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        MissionRepository missionRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        JwtAccessTokenService jwtAccessTokenService,
        PlatformTransactionManager transactionManager,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.missionRepository = missionRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
    }

    @Test
    void approve_contentSetMission_returnsPublishedMissionAndSuccessAudit() throws Exception {
        Fixture fixture = createFixture(
            MissionConditionType.CONTENT_SET,
            ContentStatus.PUBLISHED,
            true,
            true,
            FUTURE_ENDS_AT,
            true
        );

        approve(fixture.adminUserId(), fixture.missionId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("미션 승인에 성공했습니다."))
            .andExpect(jsonPath("$.data.missionId").value(fixture.missionId().toString()))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.publishedAt", endsWith("Z")));

        Mission approvedMission = missionRepository.findById(fixture.missionId()).orElseThrow();
        assertThat(approvedMission.getStatus()).isEqualTo(MissionStatus.PUBLISHED);
        assertThat(approvedMission.getPublishedAt()).isNotNull();
        assertThat(auditEventRepository.findAll())
            .singleElement()
            .satisfies(event -> assertSuccessAudit(event, fixture));
        assertThat(auditEventActorLinkRepository.count()).isEqualTo(1);
    }

    @Test
    void reject_withAllowedReasonCode_returnsDraftMissionAndSuccessAudit() throws Exception {
        Fixture fixture = createFixture(
            MissionConditionType.VISIT_COUNT,
            ContentStatus.PUBLISHED,
            true,
            true,
            FUTURE_ENDS_AT,
            true
        );

        mockMvc.perform(post("/api/v1/region-admin/missions/{missionId}/reject", fixture.missionId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(fixture.adminUserId()))
                .contentType("application/json")
                .content("{\"reasonCode\":\"MISSION_REWARD_POLICY_INVALID\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.rejectedAt", endsWith("Z")));

        assertMissionState(fixture.missionId(), MissionStatus.DRAFT);
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getPreviousState()).isEqualTo("PENDING_REVIEW");
            assertThat(event.getNextState()).isEqualTo("DRAFT");
            assertThat(event.getReasonCode()).isEqualTo("MISSION_REWARD_POLICY_INVALID");
        });
    }

    @Test
    void reject_withDisallowedReasonCode_returnsInvalidInputWithoutChangingMission() throws Exception {
        Fixture fixture = createFixture(
            MissionConditionType.VISIT_COUNT,
            ContentStatus.PUBLISHED,
            true,
            true,
            FUTURE_ENDS_AT,
            true
        );

        mockMvc.perform(post("/api/v1/region-admin/missions/{missionId}/reject", fixture.missionId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(fixture.adminUserId()))
                .contentType("application/json")
                .content("{\"reasonCode\":\"PERSONAL_OPINION\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertMissionPending(fixture.missionId());
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void reject_whenAdminBelongsToOtherRegion_returnsForbiddenAndRecordsFailureAudit() throws Exception {
        Fixture fixture = createFixture(
            MissionConditionType.VISIT_COUNT,
            ContentStatus.PUBLISHED,
            true,
            true,
            FUTURE_ENDS_AT,
            true
        );
        Long otherAdminUserId = createOtherRegionAdmin();

        mockMvc.perform(post("/api/v1/region-admin/missions/{missionId}/reject", fixture.missionId())
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + jwtAccessTokenService.issue(otherAdminUserId)
                )
                .contentType("application/json")
                .content("{\"reasonCode\":\"MISSION_REWARD_POLICY_INVALID\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertMissionPending(fixture.missionId());
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getTargetType()).isEqualTo(AuditEventTargetType.MISSION);
            assertThat(event.getTargetId()).isEqualTo(fixture.missionId());
            assertThat(event.getPreviousState()).isEqualTo(MissionStatus.PENDING_REVIEW.name());
            assertThat(event.getNextState()).isNull();
            assertThat(event.getResult()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(event.getReasonCode()).isEqualTo("FORBIDDEN");
        });
        assertThat(auditEventActorLinkRepository.count()).isEqualTo(1);
    }

    @Test
    void approve_whenTargetContentIsNotPublished_returnsMissionStateConflict() throws Exception {
        Fixture fixture = createFixture(
            MissionConditionType.CONTENT_SET,
            ContentStatus.APPROVED,
            true,
            true,
            FUTURE_ENDS_AT,
            true
        );

        assertApprovalError(fixture, ErrorCode.MISSION_STATE_CONFLICT);
    }

    @Test
    void approve_whenRewardPolicyIsDraft_returnsMissionStateConflict() throws Exception {
        Fixture fixture = createFixture(
            MissionConditionType.VISIT_COUNT,
            ContentStatus.PUBLISHED,
            false,
            true,
            FUTURE_ENDS_AT,
            true
        );

        assertApprovalError(fixture, ErrorCode.MISSION_STATE_CONFLICT);
    }

    @Test
    void approve_whenRegionIsPrivate_returnsMissionStateConflict() throws Exception {
        Fixture fixture = createFixture(
            MissionConditionType.VISIT_COUNT,
            ContentStatus.PUBLISHED,
            true,
            false,
            FUTURE_ENDS_AT,
            true
        );

        assertApprovalError(fixture, ErrorCode.MISSION_STATE_CONFLICT);
    }

    @Test
    void approve_whenMissionIsNotPendingReview_returnsMissionStateConflict() throws Exception {
        Fixture fixture = createFixture(
            MissionConditionType.VISIT_COUNT,
            ContentStatus.PUBLISHED,
            true,
            true,
            FUTURE_ENDS_AT,
            false
        );

        approve(fixture.adminUserId(), fixture.missionId())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MISSION_STATE_CONFLICT"));
        assertMissionState(fixture.missionId(), MissionStatus.DRAFT);
    }

    @Test
    void approve_whenMissionEndHasPassed_returnsInvalidInput() throws Exception {
        Fixture fixture = createFixture(
            MissionConditionType.VISIT_COUNT,
            ContentStatus.PUBLISHED,
            true,
            true,
            EXPIRED_ENDS_AT,
            true
        );

        assertApprovalError(fixture, ErrorCode.INVALID_INPUT);
    }

    @Test
    void approve_whenAdminBelongsToOtherRegion_returnsForbidden() throws Exception {
        Fixture fixture = createFixture(
            MissionConditionType.VISIT_COUNT,
            ContentStatus.PUBLISHED,
            true,
            true,
            FUTURE_ENDS_AT,
            true
        );
        Long otherAdminUserId = createOtherRegionAdmin();

        approve(otherAdminUserId, fixture.missionId())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertMissionPending(fixture.missionId());
    }

    private void assertApprovalError(
        Fixture fixture,
        ErrorCode errorCode
    ) throws Exception {
        approve(fixture.adminUserId(), fixture.missionId())
            .andExpect(status().is(errorCode.httpStatus().value()))
            .andExpect(jsonPath("$.code").value(errorCode.code()));
        assertMissionPending(fixture.missionId());
    }

    private ResultActions approve(
        Long userId,
        Long missionId
    ) throws Exception {
        return mockMvc.perform(post(
                "/api/v1/region-admin/missions/{missionId}/approve",
                missionId
            )
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtAccessTokenService.issue(userId)
            ));
    }

    private void assertMissionPending(Long missionId) {
        assertMissionState(missionId, MissionStatus.PENDING_REVIEW);
    }

    private void assertMissionState(
        Long missionId,
        MissionStatus expectedStatus
    ) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        assertThat(mission.getStatus()).isEqualTo(expectedStatus);
        assertThat(mission.getPublishedAt()).isNull();
    }

    private void assertSuccessAudit(
        AuditEvent event,
        Fixture fixture
    ) {
        assertThat(event.getRegion().getRegionId()).isEqualTo(fixture.regionId());
        assertThat(event.getTargetType()).isEqualTo(AuditEventTargetType.MISSION);
        assertThat(event.getTargetId()).isEqualTo(fixture.missionId());
        assertThat(event.getPreviousState()).isEqualTo("PENDING_REVIEW");
        assertThat(event.getNextState()).isEqualTo("PUBLISHED");
        assertThat(event.getResult()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(event.getReasonCode()).isEqualTo("MISSION_APPROVED");
        assertThat(event.getActorKind()).isEqualTo("USER");
        assertThat(event.getActorRole()).isEqualTo("REGION_ADMIN");
    }

    private Fixture createFixture(
        MissionConditionType conditionType,
        ContentStatus targetContentStatus,
        boolean publishCouponPolicy,
        boolean publicRegion,
        Instant endsAt,
        boolean pendingReview
    ) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region(
                "APPROVE-" + suffix,
                "Gimhae",
                publicRegion
            ));
            AppUser admin = saveUser("admin-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                admin,
                UserRole.REGION_ADMIN,
                region
            ));
            Content rewardContent = contentRepository.save(newContent(
                region,
                admin,
                ContentStatus.PUBLISHED,
                "reward"
            ));
            CouponPolicy rewardCouponPolicy = new CouponPolicy(
                rewardContent,
                region,
                "Mission reward",
                null,
                CouponIssuanceType.MISSION_REWARD,
                1_000,
                1_000,
                7,
                BASE_TIME.minusSeconds(3_600),
                Instant.parse("2099-12-31T00:00:00Z"),
                null
            );
            if (publishCouponPolicy) {
                rewardCouponPolicy.publish(BASE_TIME);
            }
            rewardCouponPolicy = couponPolicyRepository.save(rewardCouponPolicy);

            Mission mission = new Mission(
                "테스트 미션",
                region,
                conditionType,
                conditionType == MissionConditionType.VISIT_COUNT ? 3 : null,
                rewardCouponPolicy,
                endsAt
            );
            if (conditionType == MissionConditionType.CONTENT_SET) {
                Content targetContent = contentRepository.save(newContent(
                    region,
                    admin,
                    targetContentStatus,
                    "target"
                ));
                mission.addTargetContent(targetContent);
            }
            mission = missionRepository.saveAndFlush(mission);
            if (pendingReview) {
                entityManager.createNativeQuery("""
                    UPDATE mission
                    SET status = 'PENDING_REVIEW'
                    WHERE mission_id = :missionId
                    """)
                    .setParameter("missionId", mission.getMissionId())
                    .executeUpdate();
            }
            entityManager.flush();
            entityManager.clear();
            return new Fixture(
                admin.getUserId(),
                region.getRegionId(),
                mission.getMissionId()
            );
        });
    }

    private Long createOtherRegionAdmin() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region(
                "OTHER-" + suffix,
                "Other region",
                true
            ));
            AppUser admin = saveUser("other-admin-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                admin,
                UserRole.REGION_ADMIN,
                region
            ));
            return admin.getUserId();
        });
    }

    private AppUser saveUser(String prefix) {
        return appUserRepository.save(new AppUser(
            prefix + "@example.com",
            "hashed-password",
            "Test user",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private Content newContent(
        Region region,
        AppUser owner,
        ContentStatus status,
        String titlePrefix
    ) {
        return new Content(
            region,
            owner,
            ContentType.EVENT_EXPERIENCE,
            status,
            titlePrefix + " content",
            "Mission test content",
            "Gimhae",
            "10:00-18:00",
            "055-1234-5678",
            "notice",
            "all",
            "none",
            "policy",
            BASE_TIME
        );
    }

    private record Fixture(
        Long adminUserId,
        Long regionId,
        Long missionId
    ) {
    }
}
