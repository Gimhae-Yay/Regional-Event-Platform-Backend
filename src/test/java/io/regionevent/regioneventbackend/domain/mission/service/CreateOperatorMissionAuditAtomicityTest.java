package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
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
class CreateOperatorMissionAuditAtomicityTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-09T00:00:00Z");

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final MissionRepository missionRepository;
    private final MissionTargetContentRepository missionTargetContentRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    CreateOperatorMissionAuditAtomicityTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        MissionRepository missionRepository,
        MissionTargetContentRepository missionTargetContentRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
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
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void createRequest_whenSuccessAuditFails_rollsBackMissionAndTargetContents() throws Exception {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        mockMvc.perform(post("/api/v1/operator/missions")
                .header("Authorization", "Bearer " + jwtAccessTokenService.issue(fixture.operator().getUserId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conditionType": "CONTENT_SET",
                      "requiredVisitCount": null,
                      "targetContentIds": ["%d"],
                      "rewardCouponPolicyId": "%d",
                      "endsAt": "2026-09-30T23:59:59+09:00"
                    }
                    """.formatted(
                    fixture.targetContent().getContentId(),
                    fixture.rewardCouponPolicy().getCouponPolicyId()
                )))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(missionRepository.count()).isZero();
        assertThat(missionTargetContentRepository.count()).isZero();
        assertFailureAudit(fixture, "INTERNAL_SERVER_ERROR");
    }

    @Test
    void createRequest_whenRewardPolicyDoesNotExist_recordsNullableTargetFailureAudit() throws Exception {
        Fixture fixture = createFixture();

        performCreate(fixture, fixture.targetContent().getContentId(), Long.MAX_VALUE)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(missionRepository.count()).isZero();
        assertThat(missionTargetContentRepository.count()).isZero();
        assertFailureAudit(fixture, "NOT_FOUND");
    }

    @Test
    void createRequest_whenTargetContentIsDeleted_recordsNullableTargetFailureAudit() throws Exception {
        Fixture fixture = createFixture();
        transactionTemplate.executeWithoutResult(status -> {
            Content targetContent = contentRepository.findById(fixture.targetContent().getContentId()).orElseThrow();
            targetContent.softDelete(CREATED_AT);
            contentRepository.saveAndFlush(targetContent);
        });

        performCreate(
            fixture,
            fixture.targetContent().getContentId(),
            fixture.rewardCouponPolicy().getCouponPolicyId()
        )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(missionRepository.count()).isZero();
        assertThat(missionTargetContentRepository.count()).isZero();
        assertFailureAudit(fixture, "NOT_FOUND");
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(
        Fixture fixture,
        Long targetContentId,
        Long rewardCouponPolicyId
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/operator/missions")
            .header("Authorization", "Bearer " + jwtAccessTokenService.issue(fixture.operator().getUserId()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "conditionType": "CONTENT_SET",
                  "requiredVisitCount": null,
                  "targetContentIds": ["%d"],
                  "rewardCouponPolicyId": "%d",
                  "endsAt": "2026-09-30T23:59:59+09:00"
                }
                """.formatted(targetContentId, rewardCouponPolicyId)));
    }

    private void assertFailureAudit(
        Fixture fixture,
        String reasonCode
    ) {
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRequestId()).isNotNull();
            assertThat(auditEvent.getRegion().getRegionId())
                .isEqualTo(fixture.rewardCouponPolicy().getRegion().getRegionId());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.MISSION);
            assertThat(auditEvent.getTargetId()).isNull();
            assertThat(auditEvent.getPreviousState()).isNull();
            assertThat(auditEvent.getNextState()).isNull();
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(auditEvent.getReasonCode()).isEqualTo(reasonCode);
        });
        AuditEvent auditEvent = auditEventRepository.findAll().getFirst();
        assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
            .hasValueSatisfying(actorLink ->
                assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.operator().getUserId())
            );
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("MSN-" + suffix, "Gimhae", true));
            AppUser operator = appUserRepository.save(new AppUser(
                "operator-" + suffix + "@example.com",
                "password-hash",
                "operator",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
            Content rewardContent = contentRepository.save(newContent(region, operator, "reward"));
            Content targetContent = contentRepository.save(newContent(region, operator, "target"));
            CouponPolicy rewardCouponPolicy = couponPolicyRepository.save(new CouponPolicy(
                rewardContent,
                region,
                "Mission reward",
                null,
                CouponIssuanceType.MISSION_REWARD,
                1_000,
                1_000,
                7,
                CREATED_AT.minusSeconds(3_600),
                CREATED_AT.plusSeconds(3_600),
                null
            ));
            return new Fixture(operator, targetContent, rewardCouponPolicy);
        });
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
        AppUser operator,
        Content targetContent,
        CouponPolicy rewardCouponPolicy
    ) {
    }

}
