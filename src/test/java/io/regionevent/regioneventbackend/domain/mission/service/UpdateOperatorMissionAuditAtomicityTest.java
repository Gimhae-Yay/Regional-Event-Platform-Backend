package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
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
import io.regionevent.regioneventbackend.domain.mission.repository.MissionTargetContentRepository;
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
class UpdateOperatorMissionAuditAtomicityTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-12T00:00:00Z");
    private static final Instant ORIGINAL_ENDS_AT = Instant.parse("2026-09-30T14:59:59Z");

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final MissionRepository missionRepository;
    private final MissionTargetContentRepository missionTargetContentRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    UpdateOperatorMissionAuditAtomicityTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        MissionRepository missionRepository,
        MissionTargetContentRepository missionTargetContentRepository,
        AuditEventRepository auditEventRepository,
        JwtAccessTokenService jwtAccessTokenService,
        PlatformTransactionManager transactionManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.missionRepository = missionRepository;
        this.missionTargetContentRepository = missionTargetContentRepository;
        this.auditEventRepository = auditEventRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void updateRequest_whenSuccessAuditFails_rollsBackMissionAndTargetReplacement() throws Exception {
        Fixture fixture = createFixture(false);
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        performUpdate(fixture)
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        transactionTemplate.executeWithoutResult(status -> {
            Mission mission = missionRepository.findMissionDetailByMissionId(fixture.missionId()).orElseThrow();
            assertThat(mission.getTitle()).isEqualTo("테스트 미션");
            assertThat(mission.getConditionType()).isEqualTo(MissionConditionType.CONTENT_SET);
            assertThat(mission.getRewardCouponPolicy().getCouponPolicyId())
                .isEqualTo(fixture.originalPolicyId());
            assertThat(mission.getEndsAt()).isEqualTo(ORIGINAL_ENDS_AT);
            assertThat(mission.getTargetContents())
                .extracting(targetContent -> targetContent.getContent().getContentId())
                .containsExactly(fixture.originalTargetId());
        });
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.missionId());
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(auditEvent.getReasonCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        });
    }

    @Test
    void updateRequest_whenMissionIsNotDraft_recordsFailureAuditAndPreservesMission() throws Exception {
        Fixture fixture = createFixture(true);

        performUpdate(fixture)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MISSION_STATE_CONFLICT"));

        assertThat(missionTargetContentRepository
            .findContentIdsByMissionIdOrderByContentIdAsc(fixture.missionId()))
            .containsExactly(fixture.originalTargetId());
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.missionId());
            assertThat(auditEvent.getPreviousState()).isEqualTo("PENDING_REVIEW");
            assertThat(auditEvent.getNextState()).isNull();
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(auditEvent.getReasonCode()).isEqualTo("MISSION_STATE_CONFLICT");
        });
    }

    private org.springframework.test.web.servlet.ResultActions performUpdate(Fixture fixture) throws Exception {
        return mockMvc.perform(patch("/api/v1/operator/missions/{missionId}", fixture.missionId())
            .header("Authorization", "Bearer " + jwtAccessTokenService.issue(fixture.operatorId()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "감사 실패 시 롤백할 제목",
                  "conditionType": "CONTENT_SET",
                  "requiredVisitCount": null,
                  "targetContentIds": ["%d"],
                  "rewardCouponPolicyId": "%d",
                  "endsAt": "2026-10-31T23:59:59+09:00"
                }
                """.formatted(fixture.requestedTargetId(), fixture.requestedPolicyId())));
    }

    private Fixture createFixture(boolean submitForReview) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("UPD-" + suffix, "Gimhae", true));
            AppUser operator = appUserRepository.save(new AppUser(
                "operator-" + suffix + "@example.com",
                "password-hash",
                "operator",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
            Content originalReward = contentRepository.save(newContent(region, operator, "original-reward"));
            Content requestedReward = contentRepository.save(newContent(region, operator, "requested-reward"));
            Content originalTarget = contentRepository.save(newContent(region, operator, "original-target"));
            Content requestedTarget = contentRepository.save(newContent(region, operator, "requested-target"));
            CouponPolicy originalPolicy = couponPolicyRepository.save(newPolicy(originalReward, region, "original"));
            CouponPolicy requestedPolicy = couponPolicyRepository.save(newPolicy(requestedReward, region, "requested"));
            Mission mission = new Mission(
                "테스트 미션",
                region,
                MissionConditionType.CONTENT_SET,
                null,
                originalPolicy,
                ORIGINAL_ENDS_AT
            );
            mission.addTargetContent(originalTarget);
            if (submitForReview) {
                mission.submitForReview();
            }
            mission = missionRepository.save(mission);
            return new Fixture(
                operator.getUserId(),
                mission.getMissionId(),
                originalPolicy.getCouponPolicyId(),
                requestedPolicy.getCouponPolicyId(),
                originalTarget.getContentId(),
                requestedTarget.getContentId()
            );
        });
    }

    private CouponPolicy newPolicy(
        Content rewardContent,
        Region region,
        String name
    ) {
        return new CouponPolicy(
            rewardContent,
            region,
            name,
            null,
            CouponIssuanceType.MISSION_REWARD,
            1_000,
            1_000,
            7,
            CREATED_AT.minusSeconds(3_600),
            CREATED_AT.plusSeconds(3_600),
            null
        );
    }

    private Content newContent(
        Region region,
        AppUser operator,
        String suffix
    ) {
        return new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.APPROVED,
            suffix + " content",
            "mission test content",
            "Gimhae",
            "10:00-18:00",
            "055-1234-5678",
            "notice",
            "all",
            "none",
            "policy",
            CREATED_AT
        );
    }

    private record Fixture(
        Long operatorId,
        Long missionId,
        Long originalPolicyId,
        Long requestedPolicyId,
        Long originalTargetId,
        Long requestedTargetId
    ) {
    }
}
