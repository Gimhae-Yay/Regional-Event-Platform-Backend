package io.regionevent.regioneventbackend.domain.platformadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeactivateAdminAccountMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final String REASON_CODE = "ADMIN_ACCOUNT_INACTIVATION";
    private static final String EVIDENCE_REFERENCE = "OPS-2026-0810-001";

    private final DeactivateAdminAccountUseCase deactivateAdminAccountUseCase;
    private final AppUserRepository appUserRepository;
    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private PlatformAdminAuthorizationService platformAdminAuthorizationService;

    @Autowired
    DeactivateAdminAccountMySqlTest(
        DeactivateAdminAccountUseCase deactivateAdminAccountUseCase,
        AppUserRepository appUserRepository,
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.deactivateAdminAccountUseCase = deactivateAdminAccountUseCase;
        this.appUserRepository = appUserRepository;
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(15)
    void 서로다른슈퍼관리자가동시에상대를비활성화하면_한명은활성으로남고감사는한건만생성된다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<DeactivationAttempt> attempts;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<DeactivationAttempt> first = executorService.submit(
                () -> deactivateAfterStart(
                    fixture.firstUser().getUserId(),
                    fixture.secondUser().getUserId(),
                    ready,
                    start
                )
            );
            Future<DeactivationAttempt> second = executorService.submit(
                () -> deactivateAfterStart(
                    fixture.secondUser().getUserId(),
                    fixture.firstUser().getUserId(),
                    ready,
                    start
                )
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );
        }

        assertThat(attempts).filteredOn(DeactivationAttempt::isSuccessful).singleElement();
        assertThat(attempts)
            .filteredOn(attempt -> !attempt.isSuccessful())
            .extracting(DeactivationAttempt::errorCode)
            .containsExactly(ErrorCode.FORBIDDEN);
        assertThat(platformAdminAssignmentRepository.findAll())
            .filteredOn(assignment -> assignment.isActive()
                && assignment.getGrade() == PlatformAdminGrade.SUPER_ADMIN)
            .singleElement()
            .extracting(PlatformAdminAssignment::getGrade)
            .isEqualTo(PlatformAdminGrade.SUPER_ADMIN);
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(this::assertSuccessfulAudit);
        assertThat(auditEventActorLinkRepository.count()).isEqualTo(1);
    }

    @Test
    @Timeout(15)
    void 비활성화처리자가기존조회후대기하면_현재잠금조회에서권한오류를반환한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch initialAuthorizationCompleted = new CountDownLatch(1);
        CountDownLatch continueDeactivation = new CountDownLatch(1);
        AtomicBoolean blockInitialAuthorization = new AtomicBoolean(true);
        doAnswer(invocation -> {
            PlatformAdminAssignment assignment = (PlatformAdminAssignment) invocation.callRealMethod();
            if (fixture.secondUser().getUserId().equals(invocation.getArgument(0))
                && blockInitialAuthorization.compareAndSet(true, false)) {
                initialAuthorizationCompleted.countDown();
                await(continueDeactivation);
            }
            return assignment;
        }).when(platformAdminAuthorizationService).requireAuthorizedSuperAdmin(anyLong());

        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            Future<DeactivationAttempt> secondRequest = executorService.submit(
                () -> deactivate(
                    fixture.secondUser().getUserId(),
                    fixture.thirdUser().getUserId()
                )
            );
            assertThat(initialAuthorizationCompleted.await(3, TimeUnit.SECONDS)).isTrue();

            DeactivationAttempt firstRequest = deactivate(
                fixture.firstUser().getUserId(),
                fixture.secondUser().getUserId()
            );
            assertThat(firstRequest.isSuccessful()).isTrue();
            continueDeactivation.countDown();

            assertThat(secondRequest.get(5, TimeUnit.SECONDS).errorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        } finally {
            continueDeactivation.countDown();
        }

        assertThat(findAssignmentStatus(fixture.secondUser().getUserId()))
            .isEqualTo(PlatformAdminAssignmentStatus.INACTIVE);
        assertThat(findAssignmentStatus(fixture.thirdUser().getUserId()))
            .isEqualTo(PlatformAdminAssignmentStatus.ACTIVE);
        assertThat(auditEventRepository.count()).isEqualTo(1);
        assertThat(auditEventActorLinkRepository.count()).isEqualTo(1);
    }

    private DeactivationAttempt deactivateAfterStart(
        Long actorUserId,
        Long targetUserId,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return deactivate(actorUserId, targetUserId);
    }

    private DeactivationAttempt deactivate(Long actorUserId, Long targetUserId) {
        try {
            return new DeactivationAttempt(deactivateAdminAccountUseCase.deactivate(
                actorUserId,
                targetUserId,
                new DeactivateAdminAccountUseCase.DeactivateAdminAccountCommand(
                    REASON_CODE,
                    EVIDENCE_REFERENCE
                ),
                UUID.randomUUID()
            ), null);
        } catch (BusinessException exception) {
            return new DeactivationAttempt(null, exception.getErrorCode());
        }
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            AppUser firstUser = saveSuperAdmin("first-" + suffix + "@example.com");
            AppUser secondUser = saveSuperAdmin("second-" + suffix + "@example.com");
            AppUser thirdUser = savePlatformAdmin("third-" + suffix + "@example.com");
            return new Fixture(firstUser, secondUser, thirdUser);
        });
    }

    private AppUser saveSuperAdmin(String email) {
        AppUser user = appUserRepository.save(new AppUser(
            email,
            "hashed-password",
            "슈퍼관리자",
            "010-1234-5678",
            AppUserAccountKind.PRIVILEGED,
            AppUserStatus.ACTIVE
        ));
        platformAdminAssignmentRepository.save(new PlatformAdminAssignment(user, PlatformAdminGrade.SUPER_ADMIN));
        return user;
    }

    private AppUser savePlatformAdmin(String email) {
        AppUser user = appUserRepository.save(new AppUser(
            email,
            "hashed-password",
            "전체관리자",
            "010-1234-5678",
            AppUserAccountKind.PRIVILEGED,
            AppUserStatus.ACTIVE
        ));
        platformAdminAssignmentRepository.save(new PlatformAdminAssignment(user, PlatformAdminGrade.PLATFORM_ADMIN));
        return user;
    }

    private PlatformAdminAssignmentStatus findAssignmentStatus(Long userId) {
        return transactionTemplate.execute(status -> platformAdminAssignmentRepository.findByAppUserUserId(userId)
            .orElseThrow()
            .getStatus());
    }

    private void assertSuccessfulAudit(AuditEvent event) {
        assertThat(event.getTargetType()).isEqualTo(AuditEventTargetType.PLATFORM_ADMIN_ASSIGNMENT);
        assertThat(event.getPreviousState()).isEqualTo(PlatformAdminAssignmentStatus.ACTIVE.name());
        assertThat(event.getNextState()).isEqualTo(PlatformAdminAssignmentStatus.INACTIVE.name());
        assertThat(event.getResult()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(event.getReasonCode()).isEqualTo(REASON_CODE);
        assertThat(event.getEvidenceReference()).isEqualTo(EVIDENCE_REFERENCE);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent deactivation did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for concurrent deactivation", exception);
        }
    }

    private record Fixture(AppUser firstUser, AppUser secondUser, AppUser thirdUser) {
    }

    private record DeactivationAttempt(
        DeactivateAdminAccountResult result,
        ErrorCode errorCode
    ) {

        private boolean isSuccessful() {
            return result != null;
        }
    }
}
