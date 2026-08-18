package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
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
import io.regionevent.regioneventbackend.domain.mission.repository.MissionUpdateSnapshot;
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
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SubmitOperatorMissionUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    private final SubmitOperatorMissionUseCase submitOperatorMissionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final MissionRepository missionRepository;
    private final AuditEventRepository auditEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    @MockitoSpyBean
    private MissionService missionService;

    @Autowired
    SubmitOperatorMissionUseCaseMySqlTest(
        SubmitOperatorMissionUseCase submitOperatorMissionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        MissionRepository missionRepository,
        AuditEventRepository auditEventRepository,
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
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(15)
    void submit_whenRequestedConcurrently_changesStateAndRecordsSuccessAuditOnce() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Attempt> attempts;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Attempt> first = executorService.submit(() -> submitAfterStart(fixture, ready, start));
            Future<Attempt> second = executorService.submit(() -> submitAfterStart(fixture, ready, start));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }

        assertThat(attempts).filteredOn(attempt -> attempt.result() != null)
            .singleElement()
            .satisfies(attempt -> assertThat(attempt.result().status()).isEqualTo(MissionStatus.PENDING_REVIEW));
        assertThat(attempts).filteredOn(attempt -> attempt.errorCode() == ErrorCode.MISSION_STATE_CONFLICT)
            .hasSize(1);
        assertThat(missionRepository.findById(fixture.missionId()))
            .hasValueSatisfying(mission -> assertThat(mission.getStatus()).isEqualTo(MissionStatus.PENDING_REVIEW));
        List<AuditEvent> auditEvents = auditEventRepository.findAll().stream()
            .filter(event -> event.getTargetType() == AuditEventTargetType.MISSION)
            .toList();
        assertThat(auditEvents)
            .filteredOn(event -> event.getResult() == AuditEventResult.SUCCESS)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getTargetId()).isEqualTo(fixture.missionId());
                assertThat(event.getResult()).isEqualTo(AuditEventResult.SUCCESS);
                assertThat(event.getPreviousState()).isEqualTo(MissionStatus.DRAFT.name());
                assertThat(event.getNextState()).isEqualTo(MissionStatus.PENDING_REVIEW.name());
                assertThat(event.getReasonCode()).isEqualTo("MISSION_SUBMITTED");
            });
        assertThat(auditEvents)
            .filteredOn(event -> event.getResult() == AuditEventResult.FAILURE)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getTargetId()).isEqualTo(fixture.missionId());
                assertThat(event.getPreviousState()).isEqualTo(MissionStatus.PENDING_REVIEW.name());
                assertThat(event.getNextState()).isNull();
                assertThat(event.getReasonCode()).isEqualTo("MISSION_STATE_CONFLICT");
            });
    }

    @Test
    @Timeout(15)
    void submit_whenRewardCouponPolicyChangesAfterInitialLookup_returnsConflictWithFailureAudit() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch initialLookupCompleted = new CountDownLatch(1);
        CountDownLatch rewardCouponPolicyChanged = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            MissionUpdateSnapshot snapshot = (MissionUpdateSnapshot) invocation.callRealMethod();
            initialLookupCompleted.countDown();
            await(rewardCouponPolicyChanged);
            return snapshot;
        }).when(missionService).findUpdateSnapshot(fixture.missionId());

        Attempt attempt;
        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            Future<Attempt> future = executorService.submit(() -> submit(fixture));
            assertThat(initialLookupCompleted.await(3, TimeUnit.SECONDS)).isTrue();
            transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery("""
                UPDATE mission
                SET reward_coupon_policy_id = :couponPolicyId
                WHERE mission_id = :missionId
                """)
                .setParameter("couponPolicyId", fixture.changedCouponPolicyId())
                .setParameter("missionId", fixture.missionId())
                .executeUpdate());
            rewardCouponPolicyChanged.countDown();
            attempt = future.get(10, TimeUnit.SECONDS);
        }

        assertThat(attempt.result()).isNull();
        assertThat(attempt.errorCode()).isEqualTo(ErrorCode.MISSION_STATE_CONFLICT);
        assertThat(missionRepository.findById(fixture.missionId()))
            .hasValueSatisfying(mission -> assertThat(mission.getStatus()).isEqualTo(MissionStatus.DRAFT));
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.MISSION)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getTargetId()).isEqualTo(fixture.missionId());
                assertThat(event.getPreviousState()).isEqualTo(MissionStatus.DRAFT.name());
                assertThat(event.getNextState()).isNull();
                assertThat(event.getResult()).isEqualTo(AuditEventResult.FAILURE);
                assertThat(event.getReasonCode()).isEqualTo("MISSION_STATE_CONFLICT");
            });
    }

    private Attempt submitAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return submit(fixture);
    }

    private Attempt submit(Fixture fixture) {
        try {
            return new Attempt(submitOperatorMissionUseCase.submit(
                fixture.operatorId(), fixture.missionId(), UUID.randomUUID()
            ), null);
        } catch (BusinessException exception) {
            return new Attempt(null, exception.getErrorCode());
        }
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("MSN-" + suffix, "Gimhae", true));
            AppUser operator = appUserRepository.save(new AppUser(
                "operator-" + suffix + "@example.com", "password-hash", "operator", "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
            Content content = contentRepository.save(new Content(
                region, operator, ContentType.EVENT_EXPERIENCE, ContentStatus.PUBLISHED,
                "mission reward content", "mission reward content description", "Gimhae", "10:00-18:00",
                "055-1234-5678", "notice", "all", "none", "policy", NOW
            ));
            CouponPolicy couponPolicy = couponPolicyRepository.save(new CouponPolicy(
                content, region, "mission reward", null, CouponIssuanceType.MISSION_REWARD,
                1_000, 1_000, 7, NOW.minusSeconds(3_600), NOW.plusSeconds(3_600), null
            ));
            Content changedPolicyContent = contentRepository.save(new Content(
                region, operator, ContentType.EVENT_EXPERIENCE, ContentStatus.PUBLISHED,
                "changed mission reward content", "changed mission reward content description", "Gimhae",
                "10:00-18:00", "055-1234-5678", "notice", "all", "none", "policy", NOW
            ));
            CouponPolicy changedCouponPolicy = couponPolicyRepository.save(new CouponPolicy(
                changedPolicyContent, region, "changed mission reward", null, CouponIssuanceType.MISSION_REWARD,
                1_000, 1_000, 7, NOW.minusSeconds(3_600), NOW.plusSeconds(3_600), null
            ));
            Mission mission = missionRepository.save(new Mission(
                "테스트 미션",
                region, MissionConditionType.VISIT_COUNT, 1, couponPolicy, NOW.plusSeconds(86_400)
            ));
            return new Fixture(operator.getUserId(), mission.getMissionId(), changedCouponPolicy.getCouponPolicyId());
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent submit did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent submit was interrupted", exception);
        }
    }

    private record Attempt(
        SubmitOperatorMissionResult result,
        ErrorCode errorCode
    ) {
    }

    private record Fixture(
        Long operatorId,
        Long missionId,
        Long changedCouponPolicyId
    ) {
    }
}
