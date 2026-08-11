package io.regionevent.regioneventbackend.domain.mission.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventActorLink;
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
class RegionAdminMissionHistoryControllerIntegrationTest {

    private static final Instant CONTENT_PUBLISHED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant COUPON_ISSUE_ENDS_AT = Instant.parse("2027-12-31T23:59:59Z");
    private static final Instant MISSION_ENDS_AT = Instant.parse("2027-12-31T23:59:59Z");

    private final MockMvc mockMvc;
    private final MissionRepository missionRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    RegionAdminMissionHistoryControllerIntegrationTest(
        MockMvc mockMvc,
        MissionRepository missionRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        JwtAccessTokenService jwtAccessTokenService,
        JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.missionRepository = missionRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void getHistory_returnsSevenActionsActorsInclusiveBoundaryAndStableOrder() throws Exception {
        Fixture fixture = createFixture("HISTORY");
        Mission mission = saveMission(fixture);
        Instant databaseNow = databaseNow();
        Instant cutoff = databaseNow.minusSeconds(90L * 24 * 60 * 60);
        Instant sameOccurredAt = databaseNow.minusSeconds(6L * 24 * 60 * 60);
        AuditEvent created = saveMissionAudit(
            fixture.region(),
            mission,
            null,
            "DRAFT",
            AuditEventResult.SUCCESS,
            "MISSION_CREATED",
            "USER",
            cutoff
        );
        AuditEvent updated = saveMissionAudit(
            fixture.region(),
            mission,
            "DRAFT",
            "DRAFT",
            AuditEventResult.SUCCESS,
            "MISSION_UPDATED",
            "USER",
            sameOccurredAt
        );
        AuditEvent submitted = saveMissionAudit(
            fixture.region(),
            mission,
            "DRAFT",
            "PENDING_REVIEW",
            AuditEventResult.SUCCESS,
            "MISSION_SUBMITTED",
            "USER",
            sameOccurredAt
        );
        AuditEvent approved = saveMissionAudit(
            fixture.region(),
            mission,
            "PENDING_REVIEW",
            "PUBLISHED",
            AuditEventResult.SUCCESS,
            "MISSION_APPROVED",
            "USER",
            databaseNow.minusSeconds(4L * 24 * 60 * 60)
        );
        AuditEvent rejected = saveMissionAudit(
            fixture.region(),
            mission,
            "PENDING_REVIEW",
            "DRAFT",
            AuditEventResult.SUCCESS,
            "MISSION_REWARD_POLICY_INVALID",
            "USER",
            databaseNow.minusSeconds(3L * 24 * 60 * 60)
        );
        AuditEvent ended = saveMissionAudit(
            fixture.region(),
            mission,
            "PUBLISHED",
            "ENDED",
            AuditEventResult.SUCCESS,
            "MISSION_OPERATION_SCHEDULE_CHANGED",
            "USER",
            databaseNow.minusSeconds(2L * 24 * 60 * 60)
        );
        AuditEvent autoEnded = saveMissionAudit(
            fixture.region(),
            mission,
            "PUBLISHED",
            "ENDED",
            AuditEventResult.SUCCESS,
            "MISSION_END_TIME_REACHED",
            "SYSTEM",
            databaseNow.minusSeconds(24 * 60 * 60)
        );
        auditEventActorLinkRepository.saveAndFlush(new AuditEventActorLink(created, fixture.admin()));
        auditEventActorLinkRepository.saveAndFlush(new AuditEventActorLink(submitted, fixture.admin()));
        saveMissionAudit(
            fixture.region(),
            mission,
            "DRAFT",
            "PENDING_REVIEW",
            AuditEventResult.FAILURE,
            "MISSION_SUBMITTED",
            "USER",
            databaseNow.minusSeconds(7L * 24 * 60 * 60)
        );
        saveMissionAudit(
            fixture.region(),
            mission,
            null,
            "DRAFT",
            AuditEventResult.SUCCESS,
            "MISSION_CREATED",
            "USER",
            cutoff.minusSeconds(1)
        );
        auditEventRepository.saveAndFlush(new AuditEvent(
            "00000000-0000-0000-0000-000000000638",
            fixture.region(),
            AuditEventTargetType.CONTENT,
            mission.getMissionId(),
            null,
            "DRAFT",
            AuditEventResult.SUCCESS,
            "MISSION_CREATED",
            "USER",
            null,
            databaseNow.minusSeconds(30)
        ));

        getHistory(fixture.admin(), mission.getMissionId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("미션 이력 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.missionId").value(mission.getMissionId().toString()))
            .andExpect(jsonPath("$.data.histories.length()").value(7))
            .andExpect(jsonPath("$.data.histories[0].auditEventId").value(created.getAuditEventId().toString()))
            .andExpect(jsonPath("$.data.histories[0].action").value("CREATED"))
            .andExpect(jsonPath("$.data.histories[0].previousStatus").isEmpty())
            .andExpect(jsonPath("$.data.histories[0].actorKind").value("USER"))
            .andExpect(jsonPath("$.data.histories[0].actorUserId").value(fixture.admin().getUserId().toString()))
            .andExpect(jsonPath("$.data.histories[0].recordedAt").value(cutoff.toString()))
            .andExpect(jsonPath("$.data.histories[1].auditEventId").value(updated.getAuditEventId().toString()))
            .andExpect(jsonPath("$.data.histories[1].action").value("UPDATED"))
            .andExpect(jsonPath("$.data.histories[1].actorKind").value("WITHDRAWN_MEMBER"))
            .andExpect(jsonPath("$.data.histories[1].actorUserId").isEmpty())
            .andExpect(jsonPath("$.data.histories[2].auditEventId").value(submitted.getAuditEventId().toString()))
            .andExpect(jsonPath("$.data.histories[2].action").value("SUBMITTED"))
            .andExpect(jsonPath("$.data.histories[3].auditEventId").value(approved.getAuditEventId().toString()))
            .andExpect(jsonPath("$.data.histories[3].action").value("APPROVED"))
            .andExpect(jsonPath("$.data.histories[4].auditEventId").value(rejected.getAuditEventId().toString()))
            .andExpect(jsonPath("$.data.histories[4].action").value("REJECTED"))
            .andExpect(jsonPath("$.data.histories[5].auditEventId").value(ended.getAuditEventId().toString()))
            .andExpect(jsonPath("$.data.histories[5].action").value("ENDED"))
            .andExpect(jsonPath("$.data.histories[6].auditEventId").value(autoEnded.getAuditEventId().toString()))
            .andExpect(jsonPath("$.data.histories[6].action").value("AUTO_ENDED"))
            .andExpect(jsonPath("$.data.histories[6].actorKind").value("SYSTEM"))
            .andExpect(jsonPath("$.data.histories[6].actorUserId").isEmpty());
    }

    @Test
    void getHistory_emptyAndAuthorizationErrors_returnsContractResponses() throws Exception {
        Fixture fixture = createFixture("EMPTY");
        Fixture otherFixture = createFixture("OTHER");
        Mission mission = saveMission(fixture);
        AppUser visitor = saveUser("visitor", AppUserStatus.ACTIVE);

        getHistory(fixture.admin(), mission.getMissionId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.histories").isArray())
            .andExpect(jsonPath("$.data.histories").isEmpty());
        getHistory(otherFixture.admin(), mission.getMissionId())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        getHistory(visitor, mission.getMissionId())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        getHistory(fixture.admin(), Long.MAX_VALUE)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/region-admin/missions/{missionId}/history", mission.getMissionId()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void getHistory_mappingMismatch_returnsInternalServerErrorWithoutPartialHistory() throws Exception {
        Fixture fixture = createFixture("MISMATCH");
        Mission mission = saveMission(fixture);
        Instant databaseNow = databaseNow();
        saveMissionAudit(
            fixture.region(),
            mission,
            null,
            "DRAFT",
            AuditEventResult.SUCCESS,
            "MISSION_CREATED",
            "USER",
            databaseNow.minusSeconds(60)
        );
        saveMissionAudit(
            fixture.region(),
            mission,
            "DRAFT",
            "PUBLISHED",
            AuditEventResult.SUCCESS,
            "MISSION_SUBMITTED",
            "USER",
            databaseNow.minusSeconds(30)
        );

        getHistory(fixture.admin(), mission.getMissionId())
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    private ResultActions getHistory(AppUser user, Long missionId) throws Exception {
        return mockMvc.perform(get("/api/v1/region-admin/missions/{missionId}/history", missionId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(user.getUserId())));
    }

    private Fixture createFixture(String prefix) {
        Region region = saveRegion(prefix);
        AppUser admin = saveUser(prefix + "-admin", AppUserStatus.ACTIVE);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
        return new Fixture(region, admin);
    }

    private Mission saveMission(Fixture fixture) {
        Content rewardContent = saveContent(fixture.region(), fixture.admin());
        CouponPolicy rewardCouponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
            rewardContent,
            fixture.region(),
            "Mission reward coupon",
            "Mission completion reward coupon",
            CouponIssuanceType.MISSION_REWARD,
            3_000,
            10_000,
            30,
            CONTENT_PUBLISHED_AT,
            COUPON_ISSUE_ENDS_AT,
            100L
        ));
        return missionRepository.saveAndFlush(new Mission(
            fixture.region(),
            MissionConditionType.VISIT_COUNT,
            3,
            rewardCouponPolicy,
            MISSION_ENDS_AT
        ));
    }

    private AuditEvent saveMissionAudit(
        Region region,
        Mission mission,
        String previousState,
        String nextState,
        AuditEventResult result,
        String reasonCode,
        String actorKind,
        Instant occurredAt
    ) {
        return auditEventRepository.saveAndFlush(new AuditEvent(
            "00000000-0000-0000-0000-000000000638",
            region,
            AuditEventTargetType.MISSION,
            mission.getMissionId(),
            previousState,
            nextState,
            result,
            reasonCode,
            actorKind,
            null,
            occurredAt
        ));
    }

    private Region saveRegion(String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return regionRepository.saveAndFlush(new Region(prefix + suffix, "Test region", true));
    }

    private AppUser saveUser(String prefix, AppUserStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + "-" + suffix + "@example.com",
            "hashed-password",
            "Test user",
            "010-1234-5678",
            status
        ));
    }

    private Content saveContent(Region region, AppUser owner) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return contentRepository.saveAndFlush(new Content(
            region,
            owner,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            suffix + " content",
            "Mission test content description",
            "Test city",
            "Every day 10:00~18:00",
            "055-1234-5678",
            "Follow safety guide",
            "Age 7+",
            "Comfortable clothes",
            "Cancel before start day",
            CONTENT_PUBLISHED_AT
        ));
    }

    private Instant databaseNow() {
        return jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class).toInstant();
    }

    private record Fixture(
        Region region,
        AppUser admin
    ) {
    }
}
