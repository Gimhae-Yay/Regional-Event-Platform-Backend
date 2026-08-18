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
class SubmitOperatorMissionAuditAtomicityTest {

    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    private final SubmitOperatorMissionUseCase submitOperatorMissionUseCase;
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
    SubmitOperatorMissionAuditAtomicityTest(
        SubmitOperatorMissionUseCase submitOperatorMissionUseCase,
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
        this.submitOperatorMissionUseCase = submitOperatorMissionUseCase;
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
    void submit_whenSuccessAuditFails_rollsBackMissionState() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> submitOperatorMissionUseCase.submit(
            fixture.operatorId(),
            fixture.missionId(),
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class);

        assertThat(missionRepository.findById(fixture.missionId()))
            .hasValueSatisfying(mission -> assertThat(mission.getStatus()).isEqualTo(MissionStatus.DRAFT));
        assertFailureAudit(fixture, fixture.operatorId(), MissionStatus.DRAFT, "INTERNAL_SERVER_ERROR");
    }

    @Test
    void submit_whenMissionIsNotDraft_preservesStateAndCommitsFailureAudit() {
        Fixture fixture = createFixture();
        transactionTemplate.executeWithoutResult(status -> {
            Mission mission = missionRepository.findById(fixture.missionId()).orElseThrow();
            mission.submitForReview();
            missionRepository.saveAndFlush(mission);
        });

        assertThatThrownBy(() -> submitOperatorMissionUseCase.submit(
            fixture.operatorId(),
            fixture.missionId(),
            UUID.randomUUID()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MISSION_STATE_CONFLICT)
        );

        assertThat(missionRepository.findById(fixture.missionId()))
            .hasValueSatisfying(mission ->
                assertThat(mission.getStatus()).isEqualTo(MissionStatus.PENDING_REVIEW)
            );
        assertFailureAudit(
            fixture,
            fixture.operatorId(),
            MissionStatus.PENDING_REVIEW,
            "MISSION_STATE_CONFLICT"
        );
    }

    @Test
    void submit_withOtherRegionOperator_preservesStateAndCommitsFailureAudit() {
        Fixture fixture = createFixture();
        Long otherOperatorId = transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region otherRegion = regionRepository.save(new Region("MSN-OTHER-" + suffix, "Busan", true));
            AppUser otherOperator = appUserRepository.save(new AppUser(
                "other-operator-" + suffix + "@example.com",
                "password-hash",
                "other operator",
                "010-9876-5432",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                otherOperator,
                UserRole.OPERATOR,
                otherRegion
            ));
            return otherOperator.getUserId();
        });

        assertThatThrownBy(() -> submitOperatorMissionUseCase.submit(
            otherOperatorId,
            fixture.missionId(),
            UUID.randomUUID()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
        );

        assertThat(missionRepository.findById(fixture.missionId()))
            .hasValueSatisfying(mission -> assertThat(mission.getStatus()).isEqualTo(MissionStatus.DRAFT));
        assertFailureAudit(fixture, otherOperatorId, MissionStatus.DRAFT, "FORBIDDEN");
    }

    @Test
    void submit_withInvalidRewardPolicy_preservesStateAndCommitsFailureAudit() {
        Fixture fixture = createFixture();
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery("""
            UPDATE coupon_policy
            SET issuance_type = 'VISIT'
            WHERE coupon_policy_id = :couponPolicyId
            """)
            .setParameter("couponPolicyId", fixture.couponPolicyId())
            .executeUpdate());

        assertThatThrownBy(() -> submitOperatorMissionUseCase.submit(
            fixture.operatorId(),
            fixture.missionId(),
            UUID.randomUUID()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MISSION_STATE_CONFLICT)
        );

        assertThat(missionRepository.findById(fixture.missionId()))
            .hasValueSatisfying(mission -> assertThat(mission.getStatus()).isEqualTo(MissionStatus.DRAFT));
        assertFailureAudit(fixture, fixture.operatorId(), MissionStatus.DRAFT, "MISSION_STATE_CONFLICT");
    }

    private void assertFailureAudit(
        Fixture fixture,
        Long actorUserId,
        MissionStatus previousState,
        String reasonCode
    ) {
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRegion().getRegionId()).isEqualTo(fixture.regionId());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.MISSION);
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.missionId());
            assertThat(auditEvent.getPreviousState()).isEqualTo(previousState.name());
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
            Content content = contentRepository.save(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PUBLISHED,
                "mission reward content",
                "mission reward content description",
                "Gimhae",
                "10:00-18:00",
                "055-1234-5678",
                "notice",
                "all",
                "none",
                "policy",
                NOW
            ));
            CouponPolicy couponPolicy = couponPolicyRepository.save(new CouponPolicy(
                content,
                region,
                "mission reward",
                null,
                CouponIssuanceType.MISSION_REWARD,
                1_000,
                1_000,
                7,
                NOW.minusSeconds(3_600),
                NOW.plusSeconds(3_600),
                null
            ));
            Mission mission = missionRepository.save(new Mission(
                "테스트 미션",
                region,
                MissionConditionType.VISIT_COUNT,
                1,
                couponPolicy,
                NOW.plusSeconds(86_400)
            ));
            return new Fixture(
                operator.getUserId(),
                region.getRegionId(),
                couponPolicy.getCouponPolicyId(),
                mission.getMissionId()
            );
        });
    }

    private record Fixture(
        Long operatorId,
        Long regionId,
        Long couponPolicyId,
        Long missionId
    ) {
    }
}
