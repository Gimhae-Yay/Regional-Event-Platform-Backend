package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
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
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    ClaimMissionRewardUseCase.class,
    FindMissionRewardClaimResultUseCase.class,
    UserRoleAssignmentService.class,
    MissionParticipationReadService.class,
    MissionParticipationService.class,
    MissionService.class,
    CouponPolicyService.class,
    MissionRewardClaimService.class,
    CouponService.class,
    CouponIssuanceService.class,
    CouponStatusHistoryService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class ClaimMissionRewardAtomicityTest extends NonTransactionalMySqlTestSupport {

    private final ClaimMissionRewardUseCase useCase;
    private final MissionService missionService;
    private final MissionRewardClaimRepository claimRepository;
    private final CouponRepository couponRepository;
    private final CouponIssuanceRepository issuanceRepository;
    private final CouponStatusHistoryRepository historyRepository;
    private final CouponPolicyRepository policyRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository userRepository;
    private final UserRoleAssignmentRepository roleRepository;
    private final ContentRepository contentRepository;
    private final MissionRepository missionRepository;
    private final MissionParticipationRepository participationRepository;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean
    private RecordAuditEventUseCase auditUseCase;

    @MockitoBean
    private RecordFailedAuditEventUseCase failedAuditUseCase;

    @MockitoBean
    private CouponIssuanceService couponIssuanceService;

    @MockitoBean
    private CouponStatusHistoryService couponStatusHistoryService;

    @Autowired
    ClaimMissionRewardAtomicityTest(
        ClaimMissionRewardUseCase useCase,
        MissionService missionService,
        MissionRewardClaimRepository claimRepository,
        CouponRepository couponRepository,
        CouponIssuanceRepository issuanceRepository,
        CouponStatusHistoryRepository historyRepository,
        CouponPolicyRepository policyRepository,
        RegionRepository regionRepository,
        AppUserRepository userRepository,
        UserRoleAssignmentRepository roleRepository,
        ContentRepository contentRepository,
        MissionRepository missionRepository,
        MissionParticipationRepository participationRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.useCase = useCase;
        this.missionService = missionService;
        this.claimRepository = claimRepository;
        this.couponRepository = couponRepository;
        this.issuanceRepository = issuanceRepository;
        this.historyRepository = historyRepository;
        this.policyRepository = policyRepository;
        this.regionRepository = regionRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.contentRepository = contentRepository;
        this.missionRepository = missionRepository;
        this.participationRepository = participationRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void 성공감사저장에실패하면수령_쿠폰_발급_상태이력_정책수량을모두롤백한다() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(auditUseCase).record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> useCase.claim(
            fixture.userId(), fixture.participationId(), UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("audit storage failure");

        assertAllRolledBack(fixture);
    }

    @Test
    void 쿠폰발급저장에실패하면모든변경을롤백한다() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("issuance storage failure"))
            .when(couponIssuanceService).create(any());

        assertThatThrownBy(() -> useCase.claim(
            fixture.userId(), fixture.participationId(), UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("issuance storage failure");

        assertAllRolledBack(fixture);
    }

    @Test
    void 쿠폰상태이력저장에실패하면모든변경을롤백한다() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("history storage failure"))
            .when(couponStatusHistoryService).create(any());

        assertThatThrownBy(() -> useCase.claim(
            fixture.userId(), fixture.participationId(), UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("history storage failure");

        assertAllRolledBack(fixture);
    }

    private void assertAllRolledBack(Fixture fixture) {
        assertThat(claimRepository.count()).isZero();
        assertThat(couponRepository.count()).isZero();
        assertThat(issuanceRepository.count()).isZero();
        assertThat(historyRepository.count()).isZero();
        assertThat(policyRepository.findById(fixture.policyId()))
            .hasValueSatisfying(policy -> assertThat(policy.getIssuedCount()).isZero());
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            Instant operationAt = missionService.findCurrentDatabaseTime();
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.saveAndFlush(new Region("A" + suffix, "김해시", true));
            AppUser operator = userRepository.saveAndFlush(user("operator-" + suffix + "@example.com"));
            AppUser visitor = userRepository.saveAndFlush(user("visitor-" + suffix + "@example.com"));
            roleRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
            Content content = contentRepository.saveAndFlush(new Content(
                region, operator, ContentType.EVENT_EXPERIENCE, ContentStatus.PUBLISHED,
                "체험", "설명", "주소", "운영시간", "055-000-0000", "안내", "전체", "복장", "정책", operationAt
            ));
            CouponPolicy policy = new CouponPolicy(
                content, region, "미션 보상", null, CouponIssuanceType.MISSION_REWARD,
                1_000, 1_000, 7, operationAt.minusSeconds(60), operationAt.plusSeconds(86_400), 10L
            );
            policy.publish(operationAt.minusSeconds(60));
            policy = policyRepository.saveAndFlush(policy);
            Mission mission = new Mission(
                region, MissionConditionType.VISIT_COUNT, 1, policy, operationAt.plusSeconds(86_400)
            );
            mission.submitForReview();
            mission.approve(operationAt.minusSeconds(60));
            mission = missionRepository.saveAndFlush(mission);
            MissionParticipation participation = new MissionParticipation(mission, visitor, operationAt.minusSeconds(600));
            participation.complete(operationAt.minusSeconds(60));
            participation = participationRepository.saveAndFlush(participation);
            return new Fixture(visitor.getUserId(), participation.getMissionParticipationId(), policy.getCouponPolicyId());
        });
    }

    private AppUser user(String loginIdentifier) {
        return new AppUser(
            loginIdentifier, "hashed-password", "사용자", "010-1234-5678", AppUserStatus.ACTIVE
        );
    }

    private record Fixture(Long userId, Long participationId, Long policyId) {
    }
}
