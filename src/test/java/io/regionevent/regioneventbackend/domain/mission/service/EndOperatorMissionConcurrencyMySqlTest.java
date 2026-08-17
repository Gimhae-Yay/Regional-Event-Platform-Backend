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
import java.util.concurrent.atomic.AtomicLong;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;
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
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
class EndOperatorMissionConcurrencyMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final Instant BASE_TIME = Instant.parse("2026-08-10T00:00:00Z");
    private static final String REASON_CODE = "MISSION_OPERATION_SCHEDULE_CHANGED";
    private static final int LOCK_WAIT_CONFIRMATION_ATTEMPTS = 30;
    private static final long LOCK_WAIT_CONFIRMATION_INTERVAL_MILLIS = 100;

    private final EndOperatorMissionUseCase endOperatorMissionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final MissionRepository missionRepository;
    private final MissionParticipationRepository missionParticipationRepository;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    @Autowired
    EndOperatorMissionConcurrencyMySqlTest(
        EndOperatorMissionUseCase endOperatorMissionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        MissionRepository missionRepository,
        MissionParticipationRepository missionParticipationRepository,
        AuditEventRepository auditEventRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager,
        EntityManager entityManager
    ) {
        this.endOperatorMissionUseCase = endOperatorMissionUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.missionRepository = missionRepository;
        this.missionParticipationRepository = missionParticipationRepository;
        this.auditEventRepository = auditEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.grantLockMonitoringPrivileges();
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(20)
    void concurrentEnds_onlyOneSucceeds() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<EndAttempt> attempts;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<EndAttempt> first = executor.submit(() -> endAfterStart(fixture, ready, start));
            Future<EndAttempt> second = executor.submit(() -> endAfterStart(fixture, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(
                first.get(15, TimeUnit.SECONDS),
                second.get(15, TimeUnit.SECONDS)
            );
        }

        assertThat(attempts).filteredOn(EndAttempt::successful).singleElement();
        assertThat(attempts)
            .filteredOn(attempt -> !attempt.successful())
            .extracting(EndAttempt::errorCode)
            .containsExactly(ErrorCode.MISSION_STATE_CONFLICT);
        assertThat(missionRepository.findById(fixture.missionId()).orElseThrow().getStatus())
            .isEqualTo(MissionStatus.ENDED);
        assertThat(missionParticipationRepository.findById(fixture.participationId()).orElseThrow().getStatus())
            .isEqualTo(MissionParticipationStatus.ENDED_INCOMPLETE);
        assertThat(auditEventRepository.count()).isEqualTo(2);
    }

    @Test
    @Timeout(20)
    void progressLocksMissionFirst_completedResultIsPreserved() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch progressLocked = new CountDownLatch(1);
        CountDownLatch releaseProgress = new CountDownLatch(1);
        AtomicLong endConnectionId = new AtomicLong();
        CountDownLatch endStarted = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Void> progress = executor.submit(() -> transactionTemplate.execute(status -> {
                missionRepository.findByMissionIdForUpdate(fixture.missionId()).orElseThrow();
                MissionParticipation participation = missionParticipationRepository
                    .findByMissionParticipationIdForUpdate(fixture.participationId())
                    .orElseThrow();
                participation.complete(BASE_TIME.plusSeconds(100));
                missionParticipationRepository.saveAndFlush(participation);
                progressLocked.countDown();
                await(releaseProgress);
                return null;
            }));
            assertThat(progressLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<EndAttempt> end = executor.submit(() -> endInTrackedTransaction(
                fixture,
                endConnectionId,
                endStarted
            ));
            assertThat(endStarted.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(awaitLockWait(endConnectionId.get())).isTrue();
            } finally {
                releaseProgress.countDown();
            }

            progress.get(10, TimeUnit.SECONDS);
            assertThat(end.get(15, TimeUnit.SECONDS).successful()).isTrue();
        }

        assertThat(missionRepository.findById(fixture.missionId()).orElseThrow().getStatus())
            .isEqualTo(MissionStatus.ENDED);
        assertThat(missionParticipationRepository.findById(fixture.participationId()).orElseThrow().getStatus())
            .isEqualTo(MissionParticipationStatus.COMPLETED);
    }

    @Test
    @Timeout(20)
    void endLocksMissionFirst_competingProgressRevalidatesAndDoesNotChangeParticipation() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch missionLockedByEnd = new CountDownLatch(1);
        CountDownLatch progressStarted = new CountDownLatch(1);
        AtomicLong progressConnectionId = new AtomicLong();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Void> end = executor.submit(() -> transactionTemplate.execute(status -> {
                missionRepository.findByMissionIdForUpdate(fixture.missionId()).orElseThrow();
                missionLockedByEnd.countDown();
                await(progressStarted);
                assertThat(awaitLockWait(progressConnectionId.get())).isTrue();
                endOperatorMissionUseCase.end(
                    fixture.operatorUserId(),
                    fixture.missionId(),
                    REASON_CODE,
                    UUID.randomUUID()
                );
                return null;
            }));
            assertThat(missionLockedByEnd.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> progress = executor.submit(() -> applyProgressAfterStateRevalidation(
                fixture,
                progressConnectionId,
                progressStarted
            ));

            end.get(15, TimeUnit.SECONDS);
            assertThat(progress.get(15, TimeUnit.SECONDS)).isFalse();
        }

        assertThat(missionRepository.findById(fixture.missionId()).orElseThrow().getStatus())
            .isEqualTo(MissionStatus.ENDED);
        assertThat(missionParticipationRepository.findById(fixture.participationId()).orElseThrow().getStatus())
            .isEqualTo(MissionParticipationStatus.ENDED_INCOMPLETE);
    }

    private EndAttempt endAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return end(fixture);
    }

    private EndAttempt end(Fixture fixture) {
        try {
            endOperatorMissionUseCase.end(
                fixture.operatorUserId(),
                fixture.missionId(),
                REASON_CODE,
                UUID.randomUUID()
            );
            return new EndAttempt(true, null);
        } catch (BusinessException exception) {
            return new EndAttempt(false, exception.getErrorCode());
        }
    }

    private EndAttempt endInTrackedTransaction(
        Fixture fixture,
        AtomicLong connectionId,
        CountDownLatch started
    ) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                connectionId.set(findCurrentConnectionId());
                started.countDown();
                endOperatorMissionUseCase.end(
                    fixture.operatorUserId(),
                    fixture.missionId(),
                    REASON_CODE,
                    UUID.randomUUID()
                );
            });
            return new EndAttempt(true, null);
        } catch (BusinessException exception) {
            return new EndAttempt(false, exception.getErrorCode());
        }
    }

    private Boolean applyProgressAfterStateRevalidation(
        Fixture fixture,
        AtomicLong connectionId,
        CountDownLatch started
    ) {
        return transactionTemplate.execute(status -> {
            connectionId.set(findCurrentConnectionId());
            started.countDown();
            Mission mission = missionRepository.findByMissionIdForUpdate(fixture.missionId()).orElseThrow();
            if (mission.getStatus() != MissionStatus.PUBLISHED) {
                return false;
            }
            MissionParticipation participation = missionParticipationRepository
                .findByMissionParticipationIdForUpdate(fixture.participationId())
                .orElseThrow();
            participation.complete(BASE_TIME.plusSeconds(100));
            missionParticipationRepository.saveAndFlush(participation);
            return true;
        });
    }

    private boolean awaitLockWait(long requestingConnectionId) {
        for (int attempt = 0; attempt < LOCK_WAIT_CONFIRMATION_ATTEMPTS; attempt++) {
            Integer waitingLockCount = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM performance_schema.data_lock_waits AS lock_wait
                    JOIN performance_schema.threads AS requesting_thread
                        ON requesting_thread.thread_id = lock_wait.requesting_thread_id
                    WHERE requesting_thread.processlist_id = ?
                    """,
                Integer.class,
                requestingConnectionId
            );
            if (waitingLockCount != null && waitingLockCount > 0) {
                return true;
            }
            awaitLockWaitConfirmationInterval();
        }
        return false;
    }

    private long findCurrentConnectionId() {
        Number connectionId = (Number) entityManager
            .createNativeQuery("SELECT CONNECTION_ID()")
            .getSingleResult();
        if (connectionId == null) {
            throw new IllegalStateException("MySQL connection id does not exist");
        }
        return connectionId.longValue();
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("MYSQL-END-" + suffix, "Gimhae", true));
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
                Instant.parse("2037-12-31T00:00:00Z"),
                null
            );
            rewardPolicy.publish(BASE_TIME);
            rewardPolicy = couponPolicyRepository.save(rewardPolicy);
            Mission mission = missionRepository.saveAndFlush(new Mission(
                region,
                MissionConditionType.VISIT_COUNT,
                3,
                rewardPolicy,
                Instant.parse("2037-09-30T14:59:59Z")
            ));
            entityManager.createNativeQuery("""
                UPDATE mission
                SET status = 'PUBLISHED', published_at = :publishedAt
                WHERE mission_id = :missionId
                """)
                .setParameter("publishedAt", BASE_TIME)
                .setParameter("missionId", mission.getMissionId())
                .executeUpdate();
            MissionParticipation participation = missionParticipationRepository.saveAndFlush(
                new MissionParticipation(
                    mission,
                    saveUser("participant-" + suffix),
                    BASE_TIME.plusSeconds(10)
                )
            );
            entityManager.clear();
            return new Fixture(
                operator.getUserId(),
                mission.getMissionId(),
                participation.getMissionParticipationId()
            );
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

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for latch", exception);
        }
    }

    private void awaitLockWaitConfirmationInterval() {
        try {
            TimeUnit.MILLISECONDS.sleep(LOCK_WAIT_CONFIRMATION_INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("lock wait confirmation was interrupted", exception);
        }
    }

    private record Fixture(
        Long operatorUserId,
        Long missionId,
        Long participationId
    ) {
    }

    private record EndAttempt(
        boolean successful,
        ErrorCode errorCode
    ) {
    }
}
