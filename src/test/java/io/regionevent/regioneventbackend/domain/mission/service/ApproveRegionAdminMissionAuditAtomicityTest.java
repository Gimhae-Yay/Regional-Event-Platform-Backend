package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class ApproveRegionAdminMissionAuditAtomicityTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-10T00:00:00Z");

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final MissionRepository missionRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    ApproveRegionAdminMissionAuditAtomicityTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        MissionRepository missionRepository,
        AuditEventRepository auditEventRepository,
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
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
    }

    @Test
    void approve_whenSuccessAuditFails_rollsBackPublicationAndRecordsFailureAudit() throws Exception {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        mockMvc.perform(post(
                "/api/v1/region-admin/missions/{missionId}/approve",
                fixture.missionId()
            )
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtAccessTokenService.issue(fixture.adminUserId())
            ))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        Mission mission = missionRepository.findById(fixture.missionId()).orElseThrow();
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.PENDING_REVIEW);
        assertThat(mission.getPublishedAt()).isNull();
        assertThat(auditEventRepository.findAll())
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getResult()).isEqualTo(AuditEventResult.FAILURE);
                assertThat(event.getReasonCode()).isEqualTo("INTERNAL_SERVER_ERROR");
                assertThat(event.getPreviousState()).isEqualTo("PENDING_REVIEW");
                assertThat(event.getNextState()).isNull();
            });
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("ATOMIC-" + suffix, "Gimhae", true));
            AppUser admin = appUserRepository.save(new AppUser(
                "admin-" + suffix + "@example.com",
                "hashed-password",
                "Region admin",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                admin,
                UserRole.REGION_ADMIN,
                region
            ));
            Content rewardContent = contentRepository.save(new Content(
                region,
                admin,
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
            ));
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
                region,
                MissionConditionType.VISIT_COUNT,
                3,
                rewardPolicy,
                Instant.parse("2099-09-30T14:59:59Z")
            ));
            entityManager.createNativeQuery("""
                UPDATE mission
                SET status = 'PENDING_REVIEW'
                WHERE mission_id = :missionId
                """)
                .setParameter("missionId", mission.getMissionId())
                .executeUpdate();
            entityManager.flush();
            entityManager.clear();
            return new Fixture(admin.getUserId(), mission.getMissionId());
        });
    }

    private record Fixture(
        Long adminUserId,
        Long missionId
    ) {
    }
}
