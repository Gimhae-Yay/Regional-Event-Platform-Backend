package io.regionevent.regioneventbackend.domain.region.service;

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
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.region.service.CreateRegionUseCase.CreateRegionCommand;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
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
class CreateRegionConcurrencyMySqlTest extends NonTransactionalMySqlTestSupport {

    private final CreateRegionUseCase createRegionUseCase;
    private final AppUserRepository appUserRepository;
    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final RegionRepository regionRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    CreateRegionConcurrencyMySqlTest(
        CreateRegionUseCase createRegionUseCase,
        AppUserRepository appUserRepository,
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository,
        RegionRepository regionRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.createRegionUseCase = createRegionUseCase;
        this.appUserRepository = appUserRepository;
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
        this.regionRepository = regionRepository;
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
    void 대소문자만다른동시지역생성_하나만성공하고나머지는중복오류를반환한다() throws Exception {
        AppUser actor = createPlatformAdmin();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<CreationAttempt> attempts;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<CreationAttempt> first = executorService.submit(
                () -> createAfterStart(actor.getUserId(), "JEONJU", ready, start)
            );
            Future<CreationAttempt> second = executorService.submit(
                () -> createAfterStart(actor.getUserId(), "jeonju", ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );
        }

        assertThat(attempts).filteredOn(CreationAttempt::isSuccessful).singleElement();
        assertThat(attempts)
            .filteredOn(attempt -> !attempt.isSuccessful())
            .extracting(CreationAttempt::errorCode)
            .containsExactly(ErrorCode.REGION_CODE_ALREADY_EXISTS);
        assertThat(regionRepository.findAll())
            .singleElement()
            .satisfies(region -> {
                assertThat(region.getRegionCode()).isEqualTo("JEONJU");
                assertThat(region.isPublic()).isFalse();
            });
        assertThat(auditEventRepository.findAll())
            .singleElement()
            .satisfies(this::assertSuccessfulRegionAudit);
        assertThat(auditEventActorLinkRepository.count()).isEqualTo(1);
    }

    private CreationAttempt createAfterStart(
        Long actorUserId,
        String regionCode,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            CreateRegionResult result = createRegionUseCase.create(
                actorUserId,
                new CreateRegionCommand(
                    regionCode,
                    "전주시",
                    "PILOT_REGION_ADDITION",
                    "OPS-2026-0805-REGION-03"
                ),
                UUID.randomUUID()
            );
            return new CreationAttempt(result, null);
        } catch (BusinessException exception) {
            return new CreationAttempt(null, exception.getErrorCode());
        }
    }

    private void assertSuccessfulRegionAudit(AuditEvent event) {
        assertThat(event.getRegion()).isNotNull();
        assertThat(event.getTargetType()).isEqualTo(AuditEventTargetType.REGION);
        assertThat(event.getTargetId()).isEqualTo(event.getRegion().getRegionId());
        assertThat(event.getPreviousState()).isNull();
        assertThat(event.getNextState()).isEqualTo("CREATED");
        assertThat(event.getResult()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(event.getReasonCode()).isEqualTo("PILOT_REGION_ADDITION");
        assertThat(event.getEvidenceReference()).isEqualTo("OPS-2026-0805-REGION-03");
        assertThat(event.getActorKind()).isEqualTo("USER");
        assertThat(event.getActorRole()).isEqualTo("PLATFORM_ADMIN");
    }

    private AppUser createPlatformAdmin() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            AppUser actor = appUserRepository.save(new AppUser(
                "platform-admin-" + suffix + "@example.com",
                "hashed-password",
                "전체관리자",
                "010-1234-5678",
                AppUserAccountKind.PRIVILEGED,
                AppUserStatus.ACTIVE
            ));
            platformAdminAssignmentRepository.save(new PlatformAdminAssignment(
                actor,
                PlatformAdminGrade.PLATFORM_ADMIN
            ));
            return actor;
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent creation did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for concurrent creation", exception);
        }
    }

    private record CreationAttempt(
        CreateRegionResult result,
        ErrorCode errorCode
    ) {

        private boolean isSuccessful() {
            return result != null;
        }
    }
}
