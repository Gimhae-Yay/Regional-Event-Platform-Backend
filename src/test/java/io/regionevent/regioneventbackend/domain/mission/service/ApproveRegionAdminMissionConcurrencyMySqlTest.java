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
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
class ApproveRegionAdminMissionConcurrencyMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final Instant BASE_TIME = Instant.parse("2026-08-10T00:00:00Z");

    private final ApproveRegionAdminMissionUseCase approveRegionAdminMissionUseCase;
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
    void targetContentLock_waitsForContentChangeAndRejectsApproval() throws Exception {
        Fixture fixture = createFixture(MissionConditionType.CONTENT_SET);
        CountDownLatch contentLocked = new CountDownLatch(1);
        CountDownLatch releaseContent = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Void> suspension = executor.submit(() -> transactionTemplate.execute(status -> {
                Content content = contentRepository.findSuspendTargetForUpdate(fixture.targetContentId())
                    .orElseThrow();
                contentLocked.countDown();
                await(releaseContent);
                content.suspend();
                contentRepository.saveAndFlush(content);
                return null;
            }));
            assertThat(contentLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ApprovalAttempt> approval = executor.submit(() -> approve(fixture));
            TimeUnit.MILLISECONDS.sleep(500);
            assertThat(approval.isDone()).isFalse();
            releaseContent.countDown();

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
                Instant.parse("2099-12-31T00:00:00Z"),
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
                Instant.parse("2099-12-31T00:00:00Z"),
                null
            );
            alternativeRewardPolicy.publish(BASE_TIME);
            alternativeRewardPolicy = couponPolicyRepository.save(alternativeRewardPolicy);
            Mission mission = new Mission(
                region,
                conditionType,
                conditionType == MissionConditionType.VISIT_COUNT ? 3 : null,
                rewardPolicy,
                Instant.parse("2099-09-30T14:59:59Z")
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

    private record Fixture(
        Long adminUserId,
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
}
