package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.platformadmin.service.DeactivateAdminAccountUseCase;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminAuthorizationLockMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final String EVIDENCE_REFERENCE = "OPS-2026-0814-001";
    private static final String REASON_CODE = "ADMIN_ACCOUNT_INACTIVATION";

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final DeactivateAdminAccountUseCase deactivateAdminAccountUseCase;
    private final ChangeRegionAdminRoleUseCase changeRegionAdminRoleUseCase;
    private final AppUserRepository appUserRepository;
    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RegionRepository regionRepository;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    AdminAuthorizationLockMySqlTest(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        DeactivateAdminAccountUseCase deactivateAdminAccountUseCase,
        ChangeRegionAdminRoleUseCase changeRegionAdminRoleUseCase,
        AppUserRepository appUserRepository,
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        RegionRepository regionRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.deactivateAdminAccountUseCase = deactivateAdminAccountUseCase;
        this.changeRegionAdminRoleUseCase = changeRegionAdminRoleUseCase;
        this.appUserRepository = appUserRepository;
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.regionRepository = regionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(15)
    void 잠금전체관리자인가가먼저시작되면_고권한계정비활성화는대기한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch authorizationLocked = new CountDownLatch(1);
        CountDownLatch releaseAuthorization = new CountDownLatch(1);
        CountDownLatch deactivationStarted = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Void> authorization = executorService.submit(() -> holdPlatformAdminAuthorization(
                fixture.platformAdmin().getUserId(),
                authorizationLocked,
                releaseAuthorization
            ));
            assertThat(authorizationLocked.await(3, TimeUnit.SECONDS)).isTrue();

            Future<Void> deactivation = executorService.submit(() -> {
                deactivationStarted.countDown();
                deactivate(fixture.superAdmin().getUserId(), fixture.platformAdmin().getUserId());
                return null;
            });
            assertThat(deactivationStarted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> deactivation.get(300, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

            releaseAuthorization.countDown();
            authorization.get(5, TimeUnit.SECONDS);
            deactivation.get(5, TimeUnit.SECONDS);
        } finally {
            releaseAuthorization.countDown();
        }

        assertThat(findPlatformAdminAssignment(fixture.platformAdmin().getUserId()).getStatus())
            .isEqualTo(PlatformAdminAssignmentStatus.INACTIVE);
    }

    @Test
    void 고권한배정비활성화가먼저커밋돼도_기존권한snapshot의잠금전체관리자인가는계정활성상태를확인한다() {
        Fixture fixture = createFixture();

        deactivate(fixture.superAdmin().getUserId(), fixture.platformAdmin().getUserId());

        PlatformAdminAssignment assignment = transactionTemplate.execute(status ->
            platformAdminAuthorizationService.requireAuthorizedPlatformAdminForUpdate(
                fixture.platformAdmin().getUserId()
            )
        );

        assertThat(assignment).isNotNull();
    }

    @Test
    @Timeout(15)
    void 잠금지역관리자인가가먼저시작되면_지역관리자역할회수는대기한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch authorizationLocked = new CountDownLatch(1);
        CountDownLatch releaseAuthorization = new CountDownLatch(1);
        CountDownLatch roleChangeStarted = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Void> authorization = executorService.submit(() -> holdRegionAdminAuthorization(
                fixture.regionAdmin().getUserId(),
                authorizationLocked,
                releaseAuthorization
            ));
            assertThat(authorizationLocked.await(3, TimeUnit.SECONDS)).isTrue();

            Future<Void> roleChange = executorService.submit(() -> {
                roleChangeStarted.countDown();
                revokeRegionAdmin(fixture);
                return null;
            });
            assertThat(roleChangeStarted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> roleChange.get(300, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

            releaseAuthorization.countDown();
            authorization.get(5, TimeUnit.SECONDS);
            roleChange.get(5, TimeUnit.SECONDS);
        } finally {
            releaseAuthorization.countDown();
        }

        assertThat(findRegionAdminAssignment(fixture.regionAdmin().getUserId()).getStatus())
            .isEqualTo(UserRoleAssignmentStatus.REVOKED);
    }

    @Test
    void 지역관리자역할회수가먼저커밋되면_잠금지역관리자인가는FORBIDDEN을반환한다() {
        Fixture fixture = createFixture();

        revokeRegionAdmin(fixture);

        assertForbidden(() -> transactionTemplate.executeWithoutResult(status ->
            regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(
                fixture.regionAdmin().getUserId()
            )
        ));
    }

    private Void holdPlatformAdminAuthorization(
        Long userId,
        CountDownLatch authorizationLocked,
        CountDownLatch releaseAuthorization
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            platformAdminAuthorizationService.requireAuthorizedPlatformAdminForUpdate(userId);
            authorizationLocked.countDown();
            await(releaseAuthorization);
        });
        return null;
    }

    private Void holdRegionAdminAuthorization(
        Long userId,
        CountDownLatch authorizationLocked,
        CountDownLatch releaseAuthorization
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(userId);
            authorizationLocked.countDown();
            await(releaseAuthorization);
        });
        return null;
    }

    private void deactivate(Long actorUserId, Long targetUserId) {
        deactivateAdminAccountUseCase.deactivate(
            actorUserId,
            targetUserId,
            new DeactivateAdminAccountUseCase.DeactivateAdminAccountCommand(
                REASON_CODE,
                EVIDENCE_REFERENCE
            ),
            UUID.randomUUID()
        );
    }

    private void revokeRegionAdmin(Fixture fixture) {
        changeRegionAdminRoleUseCase.change(
            fixture.platformAdmin().getUserId(),
            fixture.regionAdmin().getUserId(),
            RegionAdminRoleChange.NONE,
            null,
            "REGION_ADMIN_REVOCATION",
            EVIDENCE_REFERENCE,
            UUID.randomUUID()
        );
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            AppUser superAdmin = savePrivilegedUser("super-" + suffix + "@example.com");
            platformAdminAssignmentRepository.saveAndFlush(new PlatformAdminAssignment(
                superAdmin,
                PlatformAdminGrade.SUPER_ADMIN
            ));
            AppUser platformAdmin = savePrivilegedUser("platform-" + suffix + "@example.com");
            platformAdminAssignmentRepository.saveAndFlush(new PlatformAdminAssignment(
                platformAdmin,
                PlatformAdminGrade.PLATFORM_ADMIN
            ));
            Region region = regionRepository.saveAndFlush(new Region(
                "R" + suffix,
                "테스트 지역",
                true
            ));
            AppUser regionAdmin = saveOrdinaryUser("region-admin-" + suffix + "@example.com");
            userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
                regionAdmin,
                UserRole.REGION_ADMIN,
                region,
                Instant.now()
            ));
            AppUser otherRegionAdmin = saveOrdinaryUser("other-region-admin-" + suffix + "@example.com");
            userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
                otherRegionAdmin,
                UserRole.REGION_ADMIN,
                region,
                Instant.now()
            ));
            return new Fixture(superAdmin, platformAdmin, regionAdmin);
        });
    }

    private AppUser savePrivilegedUser(String email) {
        return appUserRepository.saveAndFlush(new AppUser(
            email,
            "hashed-password",
            "고권한 관리자",
            "010-1234-5678",
            AppUserAccountKind.PRIVILEGED,
            AppUserStatus.ACTIVE
        ));
    }

    private AppUser saveOrdinaryUser(String email) {
        return appUserRepository.saveAndFlush(new AppUser(
            email,
            "hashed-password",
            "지역관리자",
            "010-9876-5432",
            AppUserAccountKind.ORDINARY,
            AppUserStatus.ACTIVE
        ));
    }

    private PlatformAdminAssignment findPlatformAdminAssignment(Long userId) {
        return transactionTemplate.execute(status -> platformAdminAssignmentRepository.findByAppUserUserId(userId)
            .orElseThrow());
    }

    private UserRoleAssignment findRegionAdminAssignment(Long userId) {
        return transactionTemplate.execute(status -> userRoleAssignmentRepository.findAllByAppUserUserId(userId)
            .stream()
            .findFirst()
            .orElseThrow());
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent request did not finish in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for concurrent request", exception);
        }
    }

    private record Fixture(AppUser superAdmin, AppUser platformAdmin, AppUser regionAdmin) {
    }
}
