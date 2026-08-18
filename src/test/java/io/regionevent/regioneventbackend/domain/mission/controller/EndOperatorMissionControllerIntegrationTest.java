package io.regionevent.regioneventbackend.domain.mission.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.MissionHistoryReadService;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionEarlyEndReasonCode;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionRewardClaim;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRewardClaimRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class EndOperatorMissionControllerIntegrationTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant FUTURE_ENDS_AT = Instant.parse("2099-09-30T14:59:59Z");
    private static final String DEFAULT_REASON_CODE = "MISSION_OPERATION_SCHEDULE_CHANGED";

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final MissionRepository missionRepository;
    private final MissionParticipationRepository missionParticipationRepository;
    private final MissionRewardClaimRepository missionRewardClaimRepository;
    private final AuditEventRepository auditEventRepository;
    private final MissionHistoryReadService missionHistoryReadService;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    @Autowired
    EndOperatorMissionControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        MissionRepository missionRepository,
        MissionParticipationRepository missionParticipationRepository,
        MissionRewardClaimRepository missionRewardClaimRepository,
        AuditEventRepository auditEventRepository,
        MissionHistoryReadService missionHistoryReadService,
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
        this.missionParticipationRepository = missionParticipationRepository;
        this.missionRewardClaimRepository = missionRewardClaimRepository;
        this.auditEventRepository = auditEventRepository;
        this.missionHistoryReadService = missionHistoryReadService;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
    }

    @ParameterizedTest
    @EnumSource(MissionEarlyEndReasonCode.class)
    void end_everyAllowedReason_returnsEndedMissionAndPreservesParticipationResults(
        MissionEarlyEndReasonCode reasonCode
    ) throws Exception {
        Fixture fixture = createFixture(true, true);

        end(fixture.operatorUserId(), fixture.missionId(), reasonCode.name())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("미션 종료에 성공했습니다."))
            .andExpect(jsonPath("$.data.missionId").value(fixture.missionId().toString()))
            .andExpect(jsonPath("$.data.status").value("ENDED"))
            .andExpect(jsonPath("$.data.endedAt", endsWith("Z")));

        Mission mission = missionRepository.findById(fixture.missionId()).orElseThrow();
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.ENDED);
        assertThat(mission.getEndedAt()).isNotNull();
        assertThat(missionParticipationRepository.findAll())
            .extracting(MissionParticipation::getStatus)
            .containsExactlyInAnyOrder(
                MissionParticipationStatus.ENDED_INCOMPLETE,
                MissionParticipationStatus.COMPLETED,
                MissionParticipationStatus.COMPLETED,
                MissionParticipationStatus.ENDED_INCOMPLETE
            );
        assertThat(missionRewardClaimRepository.findAll())
            .singleElement()
            .extracting(claim -> claim.getMissionParticipation().getMissionParticipationId())
            .isEqualTo(fixture.claimedParticipationId());
        assertThat(auditEventRepository.findAll())
            .singleElement()
            .satisfies(event -> assertSuccessAudit(event, fixture, reasonCode.name()));
        assertThat(missionHistoryReadService.findAll(fixture.missionId()))
            .singleElement()
            .extracting(history -> history.action())
            .isEqualTo("ENDED");
    }

    @Test
    void end_unsupportedReason_returnsInvalidInputWithoutLookupOrAudit() throws Exception {
        Fixture fixture = createFixture(true, false);

        end(fixture.operatorUserId(), fixture.missionId(), "MISSION_OPERATION_SCHEDULE_CHANGED ")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertMissionState(fixture.missionId(), MissionStatus.PUBLISHED);
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void end_missingReason_returnsInvalidInputWithoutAudit() throws Exception {
        Fixture fixture = createFixture(true, false);

        perform(fixture.operatorUserId(), fixture.missionId().toString(), "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void end_invalidJson_returnsInvalidJsonWithoutAudit() throws Exception {
        Fixture fixture = createFixture(true, false);

        perform(fixture.operatorUserId(), fixture.missionId().toString(), "{")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void end_invalidReasonType_returnsInvalidTypeWithoutAudit() throws Exception {
        Fixture fixture = createFixture(true, false);

        perform(fixture.operatorUserId(), fixture.missionId().toString(), "{\"reasonCode\":123}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void end_invalidPath_returnsInvalidTypeWithoutAudit() throws Exception {
        Fixture fixture = createFixture(true, false);

        perform(fixture.operatorUserId(), "mission", requestBody(DEFAULT_REASON_CODE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void end_withoutAuthentication_returnsUnauthenticatedWithoutAudit() throws Exception {
        Fixture fixture = createFixture(true, false);

        mockMvc.perform(post("/api/v1/operator/missions/{missionId}/end", fixture.missionId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(DEFAULT_REASON_CODE)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void end_userWithoutOperatorRole_returnsForbiddenWithoutAudit() throws Exception {
        Fixture fixture = createFixture(true, false);
        Long userId = createUserWithoutRole();

        end(userId, fixture.missionId(), DEFAULT_REASON_CODE)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void end_otherRegionOperator_returnsForbiddenAndRecordsFailureAudit() throws Exception {
        Fixture fixture = createFixture(true, false);
        Long otherOperatorId = createOtherRegionOperator();

        end(otherOperatorId, fixture.missionId(), DEFAULT_REASON_CODE)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertMissionState(fixture.missionId(), MissionStatus.PUBLISHED);
        assertFailureAudit("FORBIDDEN", "PUBLISHED");
    }

    @Test
    void end_unknownMission_returnsNotFoundWithoutAudit() throws Exception {
        Fixture fixture = createFixture(true, false);

        end(fixture.operatorUserId(), Long.MAX_VALUE, DEFAULT_REASON_CODE)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void end_nonPublishedMission_returnsConflictAndRecordsFailureAudit() throws Exception {
        Fixture fixture = createFixture(false, false);

        end(fixture.operatorUserId(), fixture.missionId(), DEFAULT_REASON_CODE)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MISSION_STATE_CONFLICT"));

        assertMissionState(fixture.missionId(), MissionStatus.DRAFT);
        assertFailureAudit("MISSION_STATE_CONFLICT", "DRAFT");
    }

    private ResultActions end(
        Long userId,
        Long missionId,
        String reasonCode
    ) throws Exception {
        return perform(userId, missionId.toString(), requestBody(reasonCode));
    }

    private ResultActions perform(
        Long userId,
        String missionId,
        String body
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/operator/missions/{missionId}/end", missionId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(userId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private String requestBody(String reasonCode) {
        return "{\"reasonCode\":\"" + reasonCode + "\"}";
    }

    private void assertMissionState(
        Long missionId,
        MissionStatus expectedStatus
    ) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        assertThat(mission.getStatus()).isEqualTo(expectedStatus);
        if (expectedStatus != MissionStatus.ENDED) {
            assertThat(mission.getEndedAt()).isNull();
        }
    }

    private void assertSuccessAudit(
        AuditEvent event,
        Fixture fixture,
        String reasonCode
    ) {
        assertThat(event.getRegion().getRegionId()).isEqualTo(fixture.regionId());
        assertThat(event.getTargetId()).isEqualTo(fixture.missionId());
        assertThat(event.getPreviousState()).isEqualTo("PUBLISHED");
        assertThat(event.getNextState()).isEqualTo("ENDED");
        assertThat(event.getResult()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(event.getReasonCode()).isEqualTo(reasonCode);
        assertThat(event.getActorRole()).isEqualTo("OPERATOR");
    }

    private void assertFailureAudit(
        String reasonCode,
        String previousState
    ) {
        assertThat(auditEventRepository.findAll())
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getResult()).isEqualTo(AuditEventResult.FAILURE);
                assertThat(event.getReasonCode()).isEqualTo(reasonCode);
                assertThat(event.getPreviousState()).isEqualTo(previousState);
                assertThat(event.getNextState()).isNull();
            });
    }

    private Fixture createFixture(
        boolean published,
        boolean withParticipations
    ) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("END-" + suffix, "Gimhae", true));
            AppUser operator = saveUser("operator-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                operator,
                UserRole.OPERATOR,
                region
            ));
            Content rewardContent = contentRepository.save(newContent(region, operator));
            CouponPolicy rewardPolicy = new CouponPolicy(
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
            rewardPolicy.publish(BASE_TIME);
            rewardPolicy = couponPolicyRepository.save(rewardPolicy);
            Mission mission = missionRepository.saveAndFlush(new Mission(
                "테스트 미션",
                region,
                MissionConditionType.VISIT_COUNT,
                3,
                rewardPolicy,
                FUTURE_ENDS_AT
            ));
            if (published) {
                entityManager.createNativeQuery("""
                    UPDATE mission
                    SET status = 'PUBLISHED', published_at = :publishedAt
                    WHERE mission_id = :missionId
                    """)
                    .setParameter("publishedAt", BASE_TIME)
                    .setParameter("missionId", mission.getMissionId())
                    .executeUpdate();
            }

            Long claimedParticipationId = null;
            if (withParticipations) {
                MissionParticipation inProgress = new MissionParticipation(
                    mission,
                    saveUser("progress-" + suffix),
                    BASE_TIME.plusSeconds(10)
                );
                MissionParticipation completedUnclaimed = new MissionParticipation(
                    mission,
                    saveUser("unclaimed-" + suffix),
                    BASE_TIME.plusSeconds(20)
                );
                completedUnclaimed.complete(BASE_TIME.plusSeconds(30));
                MissionParticipation completedClaimed = new MissionParticipation(
                    mission,
                    saveUser("claimed-" + suffix),
                    BASE_TIME.plusSeconds(40)
                );
                completedClaimed.complete(BASE_TIME.plusSeconds(50));
                MissionParticipation endedIncomplete = new MissionParticipation(
                    mission,
                    saveUser("ended-" + suffix),
                    BASE_TIME.plusSeconds(60)
                );
                endedIncomplete.endIncomplete();
                missionParticipationRepository.saveAll(List.of(
                    inProgress,
                    completedUnclaimed,
                    completedClaimed,
                    endedIncomplete
                ));
                missionParticipationRepository.flush();
                missionRewardClaimRepository.save(new MissionRewardClaim(
                    completedClaimed,
                    rewardPolicy,
                    BASE_TIME.plusSeconds(70)
                ));
                claimedParticipationId = completedClaimed.getMissionParticipationId();
            }
            entityManager.flush();
            entityManager.clear();
            return new Fixture(
                operator.getUserId(),
                region.getRegionId(),
                mission.getMissionId(),
                claimedParticipationId
            );
        });
    }

    private Long createUserWithoutRole() {
        return transactionTemplate.execute(status -> saveUser("user-" + System.nanoTime()).getUserId());
    }

    private Long createOtherRegionOperator() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("OTHER-" + suffix, "Other", true));
            AppUser operator = saveUser("other-operator-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                operator,
                UserRole.OPERATOR,
                region
            ));
            return operator.getUserId();
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
        AppUser owner
    ) {
        return new Content(
            region,
            owner,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "Reward content",
            "Mission reward content",
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
        Long operatorUserId,
        Long regionId,
        Long missionId,
        Long claimedParticipationId
    ) {
    }
}
