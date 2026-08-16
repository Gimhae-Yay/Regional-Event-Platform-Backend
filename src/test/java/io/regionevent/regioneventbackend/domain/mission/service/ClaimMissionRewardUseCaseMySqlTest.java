package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActorLinkService;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventService;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponIssuanceRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponStatusHistoryRepository;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponIssuanceService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRewardClaimRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    ClaimMissionRewardUseCase.class,
    FindMissionRewardClaimResultUseCase.class,
    AppUserService.class,
    UserRoleAssignmentService.class,
    MissionParticipationReadService.class,
    MissionParticipationService.class,
    MissionService.class,
    CouponPolicyService.class,
    MissionRewardClaimService.class,
    CouponService.class,
    CouponIssuanceService.class,
    CouponStatusHistoryService.class,
    AuditEventService.class,
    AuditEventActorLinkService.class,
    RecordAuditEventUseCase.class,
    RecordFailedAuditEventUseCase.class
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClaimMissionRewardUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private final ClaimMissionRewardUseCase useCase;
    private final MissionRewardClaimRepository claimRepository;
    private final CouponRepository couponRepository;
    private final CouponIssuanceRepository issuanceRepository;
    private final CouponStatusHistoryRepository historyRepository;
    private final AuditEventRepository auditRepository;
    private final CouponPolicyRepository policyRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository userRepository;
    private final UserRoleAssignmentRepository roleRepository;
    private final ContentRepository contentRepository;
    private final MissionRepository missionRepository;
    private final MissionParticipationRepository participationRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AppUserService appUserService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @Autowired
    ClaimMissionRewardUseCaseMySqlTest(
        ClaimMissionRewardUseCase useCase,
        MissionRewardClaimRepository claimRepository,
        CouponRepository couponRepository,
        CouponIssuanceRepository issuanceRepository,
        CouponStatusHistoryRepository historyRepository,
        AuditEventRepository auditRepository,
        CouponPolicyRepository policyRepository,
        RegionRepository regionRepository,
        AppUserRepository userRepository,
        UserRoleAssignmentRepository roleRepository,
        ContentRepository contentRepository,
        MissionRepository missionRepository,
        MissionParticipationRepository participationRepository,
        PlatformTransactionManager transactionManager,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.useCase = useCase;
        this.claimRepository = claimRepository;
        this.couponRepository = couponRepository;
        this.issuanceRepository = issuanceRepository;
        this.historyRepository = historyRepository;
        this.auditRepository = auditRepository;
        this.policyRepository = policyRepository;
        this.regionRepository = regionRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.contentRepository = contentRepository;
        this.missionRepository = missionRepository;
        this.participationRepository = participationRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void 순차반복과미션종료후재요청은최초결과를반환한다() {
        Fixture fixture = createFixture(10L);

        ClaimMissionRewardResult first = claim(fixture);
        endMission(fixture.missionId());
        ClaimMissionRewardResult repeated = claim(fixture);

        assertThat(repeated).isEqualTo(first);
        assertSinglePersistence(fixture.policyId());
    }

    @Test
    @Timeout(15)
    void 발급한도하나에서동시요청은동일한최초결과로수렴한다() throws Exception {
        Fixture fixture = createFixture(1L);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ClaimMissionRewardResult> firstFuture = executor.submit(() -> claimAfterStart(fixture, start));
            Future<ClaimMissionRewardResult> secondFuture = executor.submit(() -> claimAfterStart(fixture, start));
            start.countDown();

            ClaimMissionRewardResult first = firstFuture.get(10, TimeUnit.SECONDS);
            ClaimMissionRewardResult second = secondFuture.get(10, TimeUnit.SECONDS);
            assertThat(second).isEqualTo(first);
        }

        assertSinglePersistence(fixture.policyId());
    }

    @Test
    void 신규수령은실제DB시각하나를모든영속시각에사용한다() {
        Fixture fixture = createFixture(10L);
        Instant before = Instant.now().minusSeconds(1);

        ClaimMissionRewardResult result = claim(fixture);

        Instant after = Instant.now().plusSeconds(1);
        assertThat(result.claimedAt()).isBetween(before, after);
        assertThat(claimRepository.findById(result.missionRewardClaimId())).hasValueSatisfying(claim ->
            assertThat(claim.getClaimedAt()).isEqualTo(result.claimedAt())
        );
        assertThat(couponRepository.findById(result.couponId())).hasValueSatisfying(coupon -> {
            assertThat(coupon.getIssuedAt()).isEqualTo(result.claimedAt());
            assertThat(coupon.getExpiresAt()).isEqualTo(result.claimedAt().plusSeconds(7 * 86_400L));
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        });
        assertThat(issuanceRepository.findByMissionRewardClaimMissionRewardClaimId(result.missionRewardClaimId()))
            .hasValueSatisfying(issuance -> assertThat(issuance.getIssuedAt()).isEqualTo(result.claimedAt()));
        assertThat(historyRepository.findFirstByCouponCouponIdOrderByOccurredAtAsc(result.couponId()))
            .hasValueSatisfying(history -> assertThat(history.getOccurredAt()).isEqualTo(result.claimedAt()));
        assertThat(auditRepository.findAll()).singleElement().satisfies(audit -> {
            assertThat(audit.getOccurredAt()).isEqualTo(result.claimedAt());
            assertThat(audit.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(audit.getReasonCode()).isEqualTo("COUPON_ISSUED");
        });
    }

    @Test
    @Timeout(15)
    void 사용자행잠금선행이면수령효과커밋후탈퇴상태전환이완료된다() throws Exception {
        Fixture fixture = createFixture(10L);
        CountDownLatch userLocked = new CountDownLatch(1);
        CountDownLatch releaseClaim = new CountDownLatch(1);

        doAnswer(invocation -> {
            Object lockedUser = invocation.callRealMethod();
            userLocked.countDown();
            await(releaseClaim);
            return lockedUser;
        }).when(appUserService).findActiveUserForUpdate(fixture.userId());

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ClaimMissionRewardResult> claim = executor.submit(() -> claim(fixture));
            assertThat(userLocked.await(3, TimeUnit.SECONDS)).isTrue();

            Future<Integer> withdrawal = executor.submit(() -> transactionTemplate.execute(status -> jdbcTemplate.update(
                "UPDATE app_user SET status = 'WITHDRAWING' WHERE user_id = ? AND status = 'ACTIVE'",
                fixture.userId()
            )));
            try {
                assertThatThrownBy(() -> withdrawal.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            } finally {
                releaseClaim.countDown();
            }

            assertThat(claim.get(5, TimeUnit.SECONDS).participationId()).isEqualTo(fixture.participationId());
            assertThat(withdrawal.get(5, TimeUnit.SECONDS)).isOne();
        } finally {
            releaseClaim.countDown();
        }

        assertSinglePersistence(fixture.policyId());
        assertThat(userRepository.findById(fixture.userId())).hasValueSatisfying(user ->
            assertThat(user.getStatus()).isEqualTo(AppUserStatus.WITHDRAWING)
        );
    }

    @Test
    @Timeout(15)
    void 탈퇴잠금선행이면탈퇴커밋후새수령효과없이금지한다() throws Exception {
        Fixture fixture = createFixture(10L);
        CountDownLatch withdrawalStarted = new CountDownLatch(1);
        CountDownLatch releaseWithdrawal = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> withdrawal = executor.submit(() -> transactionTemplate.execute(status -> {
                userRepository.findByIdForUpdate(fixture.userId()).orElseThrow();
                int updatedCount = jdbcTemplate.update(
                    "UPDATE app_user SET status = 'WITHDRAWING' WHERE user_id = ? AND status = 'ACTIVE'",
                    fixture.userId()
                );
                withdrawalStarted.countDown();
                await(releaseWithdrawal);
                return updatedCount;
            }));
            assertThat(withdrawalStarted.await(3, TimeUnit.SECONDS)).isTrue();

            Future<ErrorCode> claim = executor.submit(() -> claimError(fixture));
            try {
                assertThatThrownBy(() -> claim.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            } finally {
                releaseWithdrawal.countDown();
            }

            assertThat(withdrawal.get(5, TimeUnit.SECONDS)).isOne();
            assertThat(claim.get(5, TimeUnit.SECONDS)).isEqualTo(ErrorCode.FORBIDDEN);
        } finally {
            releaseWithdrawal.countDown();
        }

        assertNoRewardPersistence(fixture.policyId());
        assertThat(userRepository.findById(fixture.userId())).hasValueSatisfying(user ->
            assertThat(user.getStatus()).isEqualTo(AppUserStatus.WITHDRAWING)
        );
    }

    @Test
    void 탈퇴로참여사용자연결이없으면null역참조와새수령효과없이금지한다() {
        Fixture fixture = createFixture(10L);
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
            "UPDATE mission_participation SET user_id = NULL WHERE mission_participation_id = ?",
            fixture.participationId()
        ));

        assertThat(claimError(fixture)).isEqualTo(ErrorCode.FORBIDDEN);

        assertNoRewardPersistence(fixture.policyId());
        assertThat(participationRepository.findById(fixture.participationId())).hasValueSatisfying(participation ->
            assertThat(participation.getUser()).isNull()
        );
    }

    private ClaimMissionRewardResult claim(Fixture fixture) {
        return useCase.claim(fixture.userId(), fixture.participationId(), UUID.randomUUID());
    }

    private ClaimMissionRewardResult claimAfterStart(Fixture fixture, CountDownLatch start) {
        await(start);
        return claim(fixture);
    }

    private ErrorCode claimError(Fixture fixture) {
        try {
            claim(fixture);
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private void assertSinglePersistence(Long policyId) {
        assertThat(claimRepository.count()).isOne();
        assertThat(couponRepository.count()).isOne();
        assertThat(issuanceRepository.count()).isOne();
        assertThat(historyRepository.count()).isOne();
        assertThat(auditRepository.count()).isOne();
        assertThat(policyRepository.findById(policyId))
            .hasValueSatisfying(policy -> assertThat(policy.getIssuedCount()).isOne());
    }

    private void assertNoRewardPersistence(Long policyId) {
        assertThat(claimRepository.count()).isZero();
        assertThat(couponRepository.count()).isZero();
        assertThat(issuanceRepository.count()).isZero();
        assertThat(historyRepository.count()).isZero();
        assertThat(auditRepository.count()).isZero();
        assertThat(policyRepository.findById(policyId))
            .hasValueSatisfying(policy -> assertThat(policy.getIssuedCount()).isZero());
    }

    private void endMission(Long missionId) {
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery("""
            UPDATE mission
            SET status = 'ENDED', ended_at = CURRENT_TIMESTAMP(6)
            WHERE mission_id = :missionId
            """).setParameter("missionId", missionId).executeUpdate());
    }

    private Fixture createFixture(Long totalIssueLimit) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.saveAndFlush(new Region("M" + suffix, "김해시", true));
            AppUser operator = userRepository.saveAndFlush(user("operator-" + suffix + "@example.com"));
            AppUser visitor = userRepository.saveAndFlush(user("visitor-" + suffix + "@example.com"));
            roleRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
            Content content = contentRepository.saveAndFlush(new Content(
                region, operator, ContentType.EVENT_EXPERIENCE, ContentStatus.PUBLISHED,
                "체험", "설명", "주소", "운영시간", "055-000-0000", "안내", "전체", "복장", "정책", now
            ));
            CouponPolicy policy = new CouponPolicy(
                content, region, "미션 보상", null, CouponIssuanceType.MISSION_REWARD,
                1_000, 1_000, 7, now.minusSeconds(3_600), now.plusSeconds(86_400), totalIssueLimit
            );
            policy.publish(now.minusSeconds(60));
            policy = policyRepository.saveAndFlush(policy);
            Mission mission = new Mission(
                region, MissionConditionType.VISIT_COUNT, 1, policy, now.plusSeconds(86_400)
            );
            mission.submitForReview();
            mission.approve(now.minusSeconds(60));
            mission = missionRepository.saveAndFlush(mission);
            MissionParticipation participation = new MissionParticipation(mission, visitor, now.minusSeconds(600));
            participation.complete(now.minusSeconds(60));
            participation = participationRepository.saveAndFlush(participation);
            return new Fixture(
                visitor.getUserId(), participation.getMissionParticipationId(), mission.getMissionId(),
                policy.getCouponPolicyId()
            );
        });
    }

    private AppUser user(String loginIdentifier) {
        return new AppUser(
            loginIdentifier, "hashed-password", "사용자", "010-1234-5678", AppUserStatus.ACTIVE
        );
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent test start timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent test interrupted", exception);
        }
    }

    private record Fixture(Long userId, Long participationId, Long missionId, Long policyId) {
    }
}
