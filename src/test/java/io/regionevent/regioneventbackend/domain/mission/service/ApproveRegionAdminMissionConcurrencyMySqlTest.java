package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
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
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@AutoConfigureMockMvc
class ApproveRegionAdminMissionConcurrencyMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final Instant BASE_TIME = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant FUTURE_ISSUE_ENDS_AT = Instant.parse("2037-12-31T00:00:00Z");
    private static final Instant FUTURE_MISSION_ENDS_AT = Instant.parse("2037-09-30T14:59:59Z");
    private static final int LOCK_WAIT_CONFIRMATION_ATTEMPTS = 30;
    private static final long LOCK_WAIT_CONFIRMATION_INTERVAL_MILLIS = 100;

    private final ApproveRegionAdminMissionUseCase approveRegionAdminMissionUseCase;
    private final MockMvc mockMvc;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final MissionRepository missionRepository;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    @Autowired
    ApproveRegionAdminMissionConcurrencyMySqlTest(
        ApproveRegionAdminMissionUseCase approveRegionAdminMissionUseCase,
        MockMvc mockMvc,
        JwtAccessTokenService jwtAccessTokenService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        MissionRepository missionRepository,
        AuditEventRepository auditEventRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager,
        EntityManager entityManager
    ) {
        this.approveRegionAdminMissionUseCase = approveRegionAdminMissionUseCase;
        this.mockMvc = mockMvc;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.missionRepository = missionRepository;
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
    void concurrentApprovals_publishExactlyOnce() throws Exception {
        Fixture fixture = createFixture(MissionConditionType.VISIT_COUNT);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<ApprovalAttempt> attempts;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ApprovalAttempt> first = executor.submit(() -> approveAfterStart(fixture, ready, start));
            Future<ApprovalAttempt> second = executor.submit(() -> approveAfterStart(fixture, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(
                first.get(15, TimeUnit.SECONDS),
                second.get(15, TimeUnit.SECONDS)
            );
        }

        assertThat(attempts).filteredOn(ApprovalAttempt::successful).singleElement();
        assertThat(attempts)
            .filteredOn(attempt -> !attempt.successful())
            .extracting(ApprovalAttempt::errorCode)
            .containsExactly(ErrorCode.MISSION_STATE_CONFLICT);
        Mission mission = missionRepository.findById(fixture.missionId()).orElseThrow();
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.PUBLISHED);
        assertThat(mission.getPublishedAt()).isNotNull();
        assertThat(auditEventRepository.count()).isEqualTo(2);
    }

    @Test
    @Timeout(20)
    void concurrentRejections_returnMissionToDraftExactlyOnce() throws Exception {
        Fixture fixture = createFixture(MissionConditionType.VISIT_COUNT);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<RejectionAttempt> attempts;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<RejectionAttempt> first = executor.submit(() -> rejectAfterStart(fixture, ready, start));
            Future<RejectionAttempt> second = executor.submit(() -> rejectAfterStart(fixture, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }

        assertThat(attempts).filteredOn(RejectionAttempt::successful).singleElement();
        assertThat(attempts)
            .filteredOn(attempt -> !attempt.successful())
            .extracting(RejectionAttempt::errorCode)
            .containsExactly(ErrorCode.MISSION_STATE_CONFLICT);
        assertThat(missionRepository.findById(fixture.missionId()).orElseThrow().getStatus())
            .isEqualTo(MissionStatus.DRAFT);
        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).hasSize(2);
        assertThat(auditEvents)
            .filteredOn(event -> event.getResult() == AuditEventResult.SUCCESS)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getTargetId()).isEqualTo(fixture.missionId());
                assertThat(event.getPreviousState()).isEqualTo(MissionStatus.PENDING_REVIEW.name());
                assertThat(event.getNextState()).isEqualTo(MissionStatus.DRAFT.name());
            });
        assertThat(auditEvents)
            .filteredOn(event -> event.getResult() == AuditEventResult.FAILURE)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getTargetId()).isEqualTo(fixture.missionId());
                assertThat(event.getPreviousState()).isEqualTo(MissionStatus.DRAFT.name());
                assertThat(event.getNextState()).isNull();
                assertThat(event.getReasonCode()).isEqualTo(ErrorCode.MISSION_STATE_CONFLICT.code());
            });
    }

    @Test
    @Timeout(20)
    void targetContentLock_waitsForContentChangeAndRejectsApproval() throws Exception {
        Fixture fixture = createFixture(MissionConditionType.CONTENT_SET);
        CountDownLatch contentLocked = new CountDownLatch(1);
        CountDownLatch releaseContent = new CountDownLatch(1);
        AtomicLong approvalConnectionId = new AtomicLong();
        CountDownLatch approvalStarted = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Void> suspension = executor.submit(() -> transactionTemplate.execute(status -> {
                regionRepository.findByRegionIdForUpdate(fixture.regionId()).orElseThrow();
                Content content = contentRepository.findSuspendTargetForUpdate(fixture.targetContentId())
                    .orElseThrow();
                contentLocked.countDown();
                await(releaseContent);
                content.suspend();
                contentRepository.saveAndFlush(content);
                return null;
            }));
            assertThat(contentLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ApprovalAttempt> approval = executor.submit(() -> approveInTrackedTransaction(
                fixture,
                approvalConnectionId,
                approvalStarted
            ));
            assertThat(approvalStarted.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(awaitLockWait(approvalConnectionId.get())).isTrue();
            } finally {
                releaseContent.countDown();
            }

            suspension.get(10, TimeUnit.SECONDS);
            assertThat(approval.get(15, TimeUnit.SECONDS).errorCode())
                .isEqualTo(ErrorCode.MISSION_STATE_CONFLICT);
        }

        Mission mission = missionRepository.findById(fixture.missionId()).orElseThrow();
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.PENDING_REVIEW);
        assertThat(contentRepository.findById(fixture.targetContentId()).orElseThrow().getStatus())
            .isEqualTo(ContentStatus.SUSPENDED);
    }

    @Test
    @Timeout(20)
    void regionVisibilityChangedBeforeRegionLock_isRejectedAfterRefresh() throws Exception {
        Fixture fixture = createFixture(MissionConditionType.VISIT_COUNT);
        CountDownLatch policyLocked = new CountDownLatch(1);
        CountDownLatch releasePolicy = new CountDownLatch(1);
        AtomicLong approvalConnectionId = new AtomicLong();
        CountDownLatch approvalStarted = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Void> policyLock = executor.submit(() -> transactionTemplate.execute(status -> {
                couponPolicyRepository.findByCouponPolicyIdForUpdate(fixture.rewardPolicyId())
                    .orElseThrow();
                policyLocked.countDown();
                await(releasePolicy);
                return null;
            }));
            assertThat(policyLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ApprovalAttempt> approval = executor.submit(() -> approveInTrackedTransaction(
                fixture,
                approvalConnectionId,
                approvalStarted
            ));
            assertThat(approvalStarted.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(awaitLockWait(approvalConnectionId.get())).isTrue();
                transactionTemplate.executeWithoutResult(status -> {
                    Region region = regionRepository.findByRegionId(fixture.regionId()).orElseThrow();
                    region.changeVisibility(false);
                    regionRepository.saveAndFlush(region);
                });
            } finally {
                releasePolicy.countDown();
            }

            policyLock.get(10, TimeUnit.SECONDS);
            assertThat(approval.get(15, TimeUnit.SECONDS).errorCode())
                .isEqualTo(ErrorCode.MISSION_STATE_CONFLICT);
        }

        Mission mission = missionRepository.findById(fixture.missionId()).orElseThrow();
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.PENDING_REVIEW);
        assertThat(regionRepository.findById(fixture.regionId()).orElseThrow().isPublic()).isFalse();
    }

    @Test
    @Timeout(20)
    void rewardPolicyLinkChangedBeforeMissionLock_isRejectedAfterRevalidation() throws Exception {
        Fixture fixture = createFixture(MissionConditionType.VISIT_COUNT);
        CountDownLatch policyLocked = new CountDownLatch(1);
        CountDownLatch releasePolicy = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Void> policyLock = executor.submit(() -> transactionTemplate.execute(status -> {
                couponPolicyRepository.findByCouponPolicyIdForUpdate(fixture.rewardPolicyId())
                    .orElseThrow();
                policyLocked.countDown();
                await(releasePolicy);
                return null;
            }));
            assertThat(policyLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ApprovalAttempt> approval = executor.submit(() -> approve(fixture));
            TimeUnit.MILLISECONDS.sleep(500);
            assertThat(approval.isDone()).isFalse();
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                "UPDATE mission SET reward_coupon_policy_id = ? WHERE mission_id = ?",
                fixture.alternativeRewardPolicyId(),
                fixture.missionId()
            ));
            releasePolicy.countDown();

            policyLock.get(10, TimeUnit.SECONDS);
            assertThat(approval.get(15, TimeUnit.SECONDS).errorCode())
                .isEqualTo(ErrorCode.MISSION_STATE_CONFLICT);
        }

        Mission mission = missionRepository.findByMissionId(fixture.missionId()).orElseThrow();
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.PENDING_REVIEW);
        assertThat(mission.getRewardCouponPolicy().getCouponPolicyId())
            .isEqualTo(fixture.alternativeRewardPolicyId());
    }

    private ApprovalAttempt approveAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return approve(fixture);
    }

    private RejectionAttempt rejectAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) throws Exception {
        ready.countDown();
        await(start);
        MvcResult result = mockMvc.perform(post(
                "/api/v1/region-admin/missions/{missionId}/reject",
                fixture.missionId()
            )
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + jwtAccessTokenService.issue(fixture.adminUserId())
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"MISSION_REWARD_POLICY_INVALID\"}"))
            .andReturn();
        int status = result.getResponse().getStatus();
        if (status == 200) {
            return new RejectionAttempt(true, null);
        }
        String responseBody = result.getResponse().getContentAsString();
        if (status == 409 && responseBody.contains("\"code\":\"MISSION_STATE_CONFLICT\"")) {
            return new RejectionAttempt(false, ErrorCode.MISSION_STATE_CONFLICT);
        }
        throw new AssertionError("unexpected rejection response: " + status + " " + responseBody);
    }

    private ApprovalAttempt approve(Fixture fixture) {
        try {
            approveRegionAdminMissionUseCase.approve(
                fixture.adminUserId(),
                fixture.missionId(),
                UUID.randomUUID()
            );
            return new ApprovalAttempt(true, null);
        } catch (BusinessException exception) {
            return new ApprovalAttempt(false, exception.getErrorCode());
        }
    }

    private ApprovalAttempt approveInTrackedTransaction(
        Fixture fixture,
        AtomicLong connectionId,
        CountDownLatch started
    ) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                connectionId.set(findCurrentConnectionId());
                started.countDown();
                approveRegionAdminMissionUseCase.approve(
                    fixture.adminUserId(),
                    fixture.missionId(),
                    UUID.randomUUID()
                );
            });
            return new ApprovalAttempt(true, null);
        } catch (BusinessException exception) {
            return new ApprovalAttempt(false, exception.getErrorCode());
        }
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

    private Fixture createFixture(MissionConditionType conditionType) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("MYSQL-" + suffix, "Gimhae", true));
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
            Content rewardContent = contentRepository.save(newContent(region, admin, "reward"));
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
                FUTURE_ISSUE_ENDS_AT,
                null
            );
            rewardPolicy.publish(BASE_TIME);
            rewardPolicy = couponPolicyRepository.save(rewardPolicy);
            Content alternativeRewardContent = contentRepository.save(newContent(
                region,
                admin,
                "alternative-reward"
            ));
            CouponPolicy alternativeRewardPolicy = new CouponPolicy(
                alternativeRewardContent,
                region,
                "Alternative mission reward",
                null,
                CouponIssuanceType.MISSION_REWARD,
                1_000,
                1_000,
                7,
                BASE_TIME.minusSeconds(3_600),
                FUTURE_ISSUE_ENDS_AT,
                null
            );
            alternativeRewardPolicy.publish(BASE_TIME);
            alternativeRewardPolicy = couponPolicyRepository.save(alternativeRewardPolicy);
            Mission mission = new Mission(
                "테스트 미션",
                region,
                conditionType,
                conditionType == MissionConditionType.VISIT_COUNT ? 3 : null,
                rewardPolicy,
                FUTURE_MISSION_ENDS_AT
            );
            Long targetContentId = null;
            if (conditionType == MissionConditionType.CONTENT_SET) {
                Content targetContent = contentRepository.save(newContent(region, admin, "target"));
                targetContentId = targetContent.getContentId();
                mission.addTargetContent(targetContent);
            }
            mission = missionRepository.saveAndFlush(mission);
            entityManager.createNativeQuery("""
                UPDATE mission
                SET status = 'PENDING_REVIEW'
                WHERE mission_id = :missionId
                """)
                .setParameter("missionId", mission.getMissionId())
                .executeUpdate();
            entityManager.flush();
            entityManager.clear();
            return new Fixture(
                admin.getUserId(),
                region.getRegionId(),
                mission.getMissionId(),
                targetContentId,
                rewardPolicy.getCouponPolicyId(),
                alternativeRewardPolicy.getCouponPolicyId()
            );
        });
    }

    private Content newContent(
        Region region,
        AppUser owner,
        String titlePrefix
    ) {
        return new Content(
            region,
            owner,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            titlePrefix + " content",
            "Mission content",
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
        Long adminUserId,
        Long regionId,
        Long missionId,
        Long targetContentId,
        Long rewardPolicyId,
        Long alternativeRewardPolicyId
    ) {
    }

    private record ApprovalAttempt(
        boolean successful,
        ErrorCode errorCode
    ) {
    }

    private record RejectionAttempt(
        boolean successful,
        ErrorCode errorCode
    ) {
    }
}
