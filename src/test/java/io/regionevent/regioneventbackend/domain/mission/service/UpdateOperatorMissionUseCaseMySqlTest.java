package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

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
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UpdateOperatorMissionUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    private final UpdateOperatorMissionUseCase updateOperatorMissionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final MissionRepository missionRepository;
    private final AuditEventRepository auditEventRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    UpdateOperatorMissionUseCaseMySqlTest(
        UpdateOperatorMissionUseCase updateOperatorMissionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        CouponPolicyRepository couponPolicyRepository,
        MissionRepository missionRepository,
        AuditEventRepository auditEventRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.updateOperatorMissionUseCase = updateOperatorMissionUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.missionRepository = missionRepository;
        this.auditEventRepository = auditEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(15)
    void update_whenPoliciesAreSwappedConcurrently_locksPoliciesInAscendingOrderWithoutDeadlock() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Attempt> attempts;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Attempt> first = executorService.submit(() -> updateAfterStart(
                fixture.firstOperatorId(),
                fixture.firstMissionId(),
                fixture.secondPolicyId(),
                ready,
                start
            ));
            Future<Attempt> second = executorService.submit(() -> updateAfterStart(
                fixture.secondOperatorId(),
                fixture.secondMissionId(),
                fixture.firstPolicyId(),
                ready,
                start
            ));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }

        assertThat(attempts).allSatisfy(attempt -> {
            assertThat(attempt.errorCode()).isNull();
            assertThat(attempt.result().status()).isEqualTo(MissionStatus.DRAFT);
        });
        assertThat(missionRepository.findByMissionId(fixture.firstMissionId())).hasValueSatisfying(mission ->
            assertThat(mission.getRewardCouponPolicy().getCouponPolicyId()).isEqualTo(fixture.secondPolicyId())
        );
        assertThat(missionRepository.findByMissionId(fixture.secondMissionId())).hasValueSatisfying(mission ->
            assertThat(mission.getRewardCouponPolicy().getCouponPolicyId()).isEqualTo(fixture.firstPolicyId())
        );
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> auditEvent.getResult() == AuditEventResult.SUCCESS)
            .hasSize(2)
            .allSatisfy(auditEvent -> assertThat(auditEvent.getReasonCode()).isEqualTo("MISSION_UPDATED"));
    }

    private Attempt updateAfterStart(
        Long operatorId,
        Long missionId,
        Long requestedPolicyId,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            UpdateOperatorMissionResult result = updateOperatorMissionUseCase.update(
                operatorId,
                missionId,
                new UpdateOperatorMissionUseCase.UpdateOperatorMissionCommand(
                    "VISIT_COUNT",
                    2,
                    List.of(),
                    requestedPolicyId,
                    OffsetDateTime.parse("2027-09-30T23:59:59+09:00")
                ),
                UUID.randomUUID()
            );
            return new Attempt(result, null);
        } catch (BusinessException exception) {
            return new Attempt(null, exception.getErrorCode());
        }
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("UPD-" + suffix, "Gimhae", true));
            AppUser firstOperator = saveOperator(region, "first-" + suffix);
            AppUser secondOperator = saveOperator(region, "second-" + suffix);
            CouponPolicy firstPolicy = savePolicy(region, firstOperator, "first");
            CouponPolicy secondPolicy = savePolicy(region, secondOperator, "second");
            Mission firstMission = missionRepository.save(new Mission(
                region,
                MissionConditionType.VISIT_COUNT,
                1,
                firstPolicy,
                NOW.plusSeconds(86_400)
            ));
            Mission secondMission = missionRepository.save(new Mission(
                region,
                MissionConditionType.VISIT_COUNT,
                1,
                secondPolicy,
                NOW.plusSeconds(86_400)
            ));
            return new Fixture(
                firstOperator.getUserId(),
                secondOperator.getUserId(),
                firstMission.getMissionId(),
                secondMission.getMissionId(),
                firstPolicy.getCouponPolicyId(),
                secondPolicy.getCouponPolicyId()
            );
        });
    }

    private AppUser saveOperator(
        Region region,
        String suffix
    ) {
        AppUser operator = appUserRepository.save(new AppUser(
            suffix + "@example.com",
            "password-hash",
            "operator",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        userRoleAssignmentRepository.save(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        return operator;
    }

    private CouponPolicy savePolicy(
        Region region,
        AppUser operator,
        String suffix
    ) {
        Content content = contentRepository.save(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            suffix + " reward content",
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
        return couponPolicyRepository.save(new CouponPolicy(
            content,
            region,
            suffix + " mission reward",
            null,
            CouponIssuanceType.MISSION_REWARD,
            1_000,
            1_000,
            7,
            NOW.minusSeconds(3_600),
            NOW.plusSeconds(3_600),
            null
        ));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent update did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent update was interrupted", exception);
        }
    }

    private record Attempt(
        UpdateOperatorMissionResult result,
        ErrorCode errorCode
    ) {
    }

    private record Fixture(
        Long firstOperatorId,
        Long secondOperatorId,
        Long firstMissionId,
        Long secondMissionId,
        Long firstPolicyId,
        Long secondPolicyId
    ) {
    }
}
