package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@SpringBootTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class RejectRegionAdminMissionAuditAtomicityTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-10T00:00:00Z");
    private static final String REASON_CODE = "MISSION_REWARD_POLICY_INVALID";

    private final RejectRegionAdminMissionUseCase rejectRegionAdminMissionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final MissionRepository missionRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    RejectRegionAdminMissionAuditAtomicityTest(
        RejectRegionAdminMissionUseCase rejectRegionAdminMissionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        MissionRepository missionRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        PlatformTransactionManager transactionManager,
        EntityManager entityManager
    ) {
        this.rejectRegionAdminMissionUseCase = rejectRegionAdminMissionUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.missionRepository = missionRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
    }

    @Test
    void reject_whenSuccessAuditFails_rollsBackStateAndCommitsFailureAudit() {
        Fixture fixture = createFixture(true);
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> rejectRegionAdminMissionUseCase.reject(
            fixture.adminUserId(),
            fixture.missionId(),
            REASON_CODE,
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class);

        assertMissionStatus(fixture.missionId(), MissionStatus.PENDING_REVIEW);
        assertFailureAudit(fixture, fixture.adminUserId(), "INTERNAL_SERVER_ERROR");
    }

    @Test
    void reject_whenMissionIsNotPendingReview_preservesStateAndCommitsFailureAudit() {
        Fixture fixture = createFixture(false);

        assertThatThrownBy(() -> rejectRegionAdminMissionUseCase.reject(
            fixture.adminUserId(),
            fixture.missionId(),
            REASON_CODE,
            UUID.randomUUID()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MISSION_STATE_CONFLICT)
        );

        assertMissionStatus(fixture.missionId(), MissionStatus.DRAFT);
        assertFailureAudit(fixture, fixture.adminUserId(), "MISSION_STATE_CONFLICT");
    }

    @Test
    void reject_withOtherRegionAdmin_preservesStateAndCommitsFailureAudit() {
        Fixture fixture = createFixture(true);
        Long otherAdminUserId = createOtherRegionAdmin();

        assertThatThrownBy(() -> rejectRegionAdminMissionUseCase.reject(
            otherAdminUserId,
            fixture.missionId(),
            REASON_CODE,
            UUID.randomUUID()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
        );

        assertMissionStatus(fixture.missionId(), MissionStatus.PENDING_REVIEW);
        assertFailureAudit(fixture, otherAdminUserId, "FORBIDDEN");
    }

    private void assertMissionStatus(
        Long missionId,
        MissionStatus status
    ) {
        assertThat(missionRepository.findById(missionId))
            .hasValueSatisfying(mission -> assertThat(mission.getStatus()).isEqualTo(status));
    }

    private void assertFailureAudit(
        Fixture fixture,
        Long actorUserId,
        String reasonCode
    ) {
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRegion().getRegionId()).isEqualTo(fixture.regionId());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.MISSION);
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.missionId());
            assertThat(auditEvent.getPreviousState()).isEqualTo(fixture.initialStatus().name());
            assertThat(auditEvent.getNextState()).isNull();
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(auditEvent.getReasonCode()).isEqualTo(reasonCode);
        });
        AuditEvent auditEvent = auditEventRepository.findAll().getFirst();
        assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
            .hasValueSatisfying(actorLink ->
                assertThat(actorLink.getActor().getUserId()).isEqualTo(actorUserId)
            );
    }

    private Fixture createFixture(boolean pendingReview) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("REJECT-" + suffix, "Gimhae", true));
            AppUser admin = saveAdmin(region, "admin-" + suffix);
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
            CouponPolicy rewardPolicy = couponPolicyRepository.save(new CouponPolicy(
                rewardContent,
                region,
                "Mission reward",
                null,
                CouponIssuanceType.MISSION_REWARD,
                1_000,
                1_000,
                7,
                BASE_TIME.minusSeconds(3_600),
                BASE_TIME.plusSeconds(86_400),
                null
            ));
            Mission mission = missionRepository.saveAndFlush(new Mission(
                region,
                MissionConditionType.VISIT_COUNT,
                3,
                rewardPolicy,
                BASE_TIME.plusSeconds(172_800)
            ));
            MissionStatus initialStatus = MissionStatus.DRAFT;
            if (pendingReview) {
                entityManager.createNativeQuery("""
                    UPDATE mission
                    SET status = 'PENDING_REVIEW'
                    WHERE mission_id = :missionId
                    """)
                    .setParameter("missionId", mission.getMissionId())
                    .executeUpdate();
                initialStatus = MissionStatus.PENDING_REVIEW;
            }
            entityManager.flush();
            entityManager.clear();
            return new Fixture(
                admin.getUserId(),
                region.getRegionId(),
                mission.getMissionId(),
                initialStatus
            );
        });
    }

    private Long createOtherRegionAdmin() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region otherRegion = regionRepository.save(new Region("REJECT-OTHER-" + suffix, "Busan", true));
            return saveAdmin(otherRegion, "other-admin-" + suffix).getUserId();
        });
    }

    private AppUser saveAdmin(
        Region region,
        String prefix
    ) {
        AppUser admin = appUserRepository.save(new AppUser(
            prefix + "@example.com",
            "hashed-password",
            "Region admin",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        userRoleAssignmentRepository.save(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
        return admin;
    }

    private record Fixture(
        Long adminUserId,
        Long regionId,
        Long missionId,
        MissionStatus initialStatus
    ) {
    }
}
