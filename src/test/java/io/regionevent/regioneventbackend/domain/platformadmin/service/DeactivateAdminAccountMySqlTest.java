package io.regionevent.regioneventbackend.domain.platformadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

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
            .containsExactly(ErrorCode.ADMIN_ACCOUNT_DEACTIVATION_CONFLICT);
        assertThat(platformAdminAssignmentRepository.findAll())
            .filteredOn(PlatformAdminAssignment::isActive)
            .singleElement()
            .extracting(PlatformAdminAssignment::getGrade)
            .isEqualTo(PlatformAdminGrade.SUPER_ADMIN);
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(this::assertSuccessfulAudit);
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
            return new Fixture(firstUser, secondUser);
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

    private record Fixture(AppUser firstUser, AppUser secondUser) {
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
