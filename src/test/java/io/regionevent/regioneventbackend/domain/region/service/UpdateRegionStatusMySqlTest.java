package io.regionevent.regioneventbackend.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventService;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.region.service.UpdateRegionStatusUseCase.UpdateRegionStatusCommand;
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
@Import(UpdateRegionStatusMySqlTest.FailingAuditEventServiceConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UpdateRegionStatusMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final String EVIDENCE_REFERENCE = "OPS-2026-0805-REGION-03";

    private final UpdateRegionStatusUseCase updateRegionStatusUseCase;
    private final RegionRepository regionRepository;
    private final ContentRepository contentRepository;
    private final AppUserRepository appUserRepository;
    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final FailingAuditEventService failingAuditEventService;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    UpdateRegionStatusMySqlTest(
        UpdateRegionStatusUseCase updateRegionStatusUseCase,
        RegionRepository regionRepository,
        ContentRepository contentRepository,
        AppUserRepository appUserRepository,
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        FailingAuditEventService failingAuditEventService,
        PlatformTransactionManager transactionManager
    ) {
        this.updateRegionStatusUseCase = updateRegionStatusUseCase;
        this.regionRepository = regionRepository;
        this.contentRepository = contentRepository;
        this.appUserRepository = appUserRepository;
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.failingAuditEventService = failingAuditEventService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @AfterEach
    void resetFailureInjection() {
        failingAuditEventService.resetFailureInjection();
    }

    @Test
    void 비삭제콘텐츠가있는공개지역의비공개요청은_실패감사를별도트랜잭션에저장한다() {
        Fixture fixture = createFixture(true, true);
        UUID requestId = UUID.randomUUID();

        assertRegionAvailabilityConflict(fixture, requestId);

        assertThat(regionRepository.findById(fixture.region().getRegionId()))
            .hasValueSatisfying(region -> assertThat(region.isPublic()).isTrue());
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRequestId()).isEqualTo(requestId.toString());
            assertThat(auditEvent.getRegion()).extracting(Region::getRegionId)
                .isEqualTo(fixture.region().getRegionId());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.REGION);
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.region().getRegionId());
            assertThat(auditEvent.getPreviousState()).isEqualTo("TRUE");
            assertThat(auditEvent.getNextState()).isNull();
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(auditEvent.getReasonCode()).isEqualTo("REGION_AVAILABILITY_CONFLICT");
            assertThat(auditEvent.getEvidenceReference()).isEqualTo(EVIDENCE_REFERENCE);
            assertThat(auditEvent.getActorKind()).isEqualTo("USER");
            assertThat(auditEvent.getActorRole()).isEqualTo("PLATFORM_ADMIN");
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                .hasValueSatisfying(link -> assertThat(link.getActor().getUserId())
                    .isEqualTo(fixture.actor().getUserId()));
        });
    }

    @Test
    void 실패감사저장이실패해도_지역미변경과충돌응답을유지하고비개인로그를남긴다() {
        Fixture fixture = createFixture(true, true);
        UUID requestId = UUID.randomUUID();
        Logger logger = (Logger) LoggerFactory.getLogger(RecordFailedAuditEventUseCase.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logger.addAppender(logAppender);
        logAppender.start();
        failingAuditEventService.failNextRecord();

        try {
            assertRegionAvailabilityConflict(fixture, requestId);
        } finally {
            logger.detachAppender(logAppender);
            logAppender.stop();
        }

        assertThat(regionRepository.findById(fixture.region().getRegionId()))
            .hasValueSatisfying(region -> assertThat(region.isPublic()).isTrue());
        assertThat(auditEventRepository.count()).isZero();
        List<ILoggingEvent> failureLogs = logAppender.list.stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .toList();
        assertThat(failureLogs).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage())
                .isEqualTo("Failed audit event write failed")
                .doesNotContain("REGION_PREPARATION")
                .doesNotContain(EVIDENCE_REFERENCE);
            assertThat(event.getKeyValuePairs())
                .extracting(pair -> pair.key, pair -> pair.value)
                .containsExactly(
                    tuple("requestId", requestId),
                    tuple("targetType", AuditEventTargetType.REGION),
                    tuple("targetId", fixture.region().getRegionId()),
                    tuple("originalErrorCode", "REGION_AVAILABILITY_CONFLICT"),
                    tuple("auditWriteResult", "FAILURE")
                );
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void 같은목표상태의동시요청은_하나의상태전이와성공감사로수렴한다() throws Exception {
        Fixture fixture = createFixture(false, false);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<UpdateRegionStatusResult>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < 2; index++) {
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("concurrent status updates did not start");
                    }
                    return updateRegionStatusUseCase.update(
                        fixture.actor().getUserId(),
                        fixture.region().getRegionId(),
                        new UpdateRegionStatusCommand(
                            true,
                            "REGION_LAUNCH",
                            EVIDENCE_REFERENCE
                        ),
                        UUID.randomUUID()
                    );
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<UpdateRegionStatusResult> future : futures) {
                assertThat(future.get(20, TimeUnit.SECONDS).isPublic()).isTrue();
            }
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(regionRepository.findById(fixture.region().getRegionId()))
            .hasValueSatisfying(region -> assertThat(region.isPublic()).isTrue());
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.REGION);
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.region().getRegionId());
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getPreviousState()).isEqualTo("FALSE");
            assertThat(auditEvent.getNextState()).isEqualTo("TRUE");
            assertThat(auditEvent.getReasonCode()).isEqualTo("REGION_LAUNCH");
        });
    }

    @Test
    @Timeout(15)
    void 고권한배정비활성화가먼저커밋되면_이미시작한지역공개요청은_FORBIDDEN으로종료하고_변경과성공감사를남기지않는다()
        throws Exception {
        Fixture fixture = createFixture(false, false);
        CountDownLatch assignmentLocked = new CountDownLatch(1);
        CountDownLatch releaseInactivation = new CountDownLatch(1);
        CountDownLatch updateStarted = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Void> inactivation = executorService.submit(() -> {
                inactivateAfterRelease(fixture, assignmentLocked, releaseInactivation);
                return null;
            });
            assertThat(assignmentLocked.await(3, TimeUnit.SECONDS)).isTrue();

            Future<ErrorCode> update = executorService.submit(() -> {
                updateStarted.countDown();
                try {
                    updateRegionStatusUseCase.update(
                        fixture.actor().getUserId(),
                        fixture.region().getRegionId(),
                        new UpdateRegionStatusCommand(
                            true,
                            "REGION_LAUNCH",
                            EVIDENCE_REFERENCE
                        ),
                        UUID.randomUUID()
                    );
                    return null;
                } catch (BusinessException exception) {
                    return exception.getErrorCode();
                }
            });
            assertThat(updateStarted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> update.get(300, TimeUnit.MILLISECONDS))
                .isInstanceOf(java.util.concurrent.TimeoutException.class);

            releaseInactivation.countDown();
            inactivation.get(5, TimeUnit.SECONDS);
            assertThat(update.get(5, TimeUnit.SECONDS)).isEqualTo(ErrorCode.FORBIDDEN);
        } finally {
            releaseInactivation.countDown();
        }

        assertThat(regionRepository.findById(fixture.region().getRegionId()))
            .hasValueSatisfying(region -> assertThat(region.isPublic()).isFalse());
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> fixture.region().getRegionId().equals(auditEvent.getTargetId()))
            .isEmpty();
        assertThat(auditEventActorLinkRepository.findAll()).isEmpty();
    }

    private void inactivateAfterRelease(
        Fixture fixture,
        CountDownLatch assignmentLocked,
        CountDownLatch releaseInactivation
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            appUserRepository.findByIdForUpdate(fixture.actor().getUserId()).orElseThrow();
            PlatformAdminAssignment assignment = platformAdminAssignmentRepository
                .findByAppUserUserId(fixture.actor().getUserId())
                .orElseThrow();
            assignmentLocked.countDown();
            await(releaseInactivation);
            assignment.inactivate(Instant.now(), "ADMIN_ACCOUNT_INACTIVATION");
        });
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

    private void assertRegionAvailabilityConflict(Fixture fixture, UUID requestId) {
        assertThatThrownBy(() -> updateRegionStatusUseCase.update(
            fixture.actor().getUserId(),
            fixture.region().getRegionId(),
            new UpdateRegionStatusCommand(
                false,
                "REGION_PREPARATION",
                EVIDENCE_REFERENCE
            ),
            requestId
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REGION_AVAILABILITY_CONFLICT)
        );
    }

    private Fixture createFixture(boolean isPublic, boolean withUndeletedContent) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region(
            "REGION-" + suffix,
            "김해시",
            isPublic
        ));
        AppUser actor = appUserRepository.saveAndFlush(new AppUser(
            "platform-admin-" + suffix + "@example.com",
            "hashed-password",
            "전체관리자",
            "010-1234-5678",
            AppUserAccountKind.PRIVILEGED,
            AppUserStatus.ACTIVE
        ));
        platformAdminAssignmentRepository.saveAndFlush(new PlatformAdminAssignment(
            actor,
            PlatformAdminGrade.PLATFORM_ADMIN
        ));

        if (withUndeletedContent) {
            Instant now = Instant.now();
            contentRepository.saveAndFlush(new Content(
                region,
                actor,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PUBLISHED,
                "김해 가야 문화 체험",
                "김해 가야 문화를 체험하는 행사입니다.",
                "김해문화의전당",
                "매일 10:00~18:00",
                "055-123-4567",
                "안전요원의 안내를 따라주세요.",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소할 수 있습니다.",
                now.minusSeconds(86_400)
            ));
        }
        return new Fixture(actor, region);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingAuditEventServiceConfiguration {

        @Bean
        @Primary
        FailingAuditEventService failingAuditEventService(AuditEventRepository auditEventRepository) {
            return new FailingAuditEventService(auditEventRepository);
        }
    }

    static class FailingAuditEventService extends AuditEventService {

        private final AtomicBoolean failNextRecord = new AtomicBoolean();

        FailingAuditEventService(AuditEventRepository auditEventRepository) {
            super(auditEventRepository);
        }

        @Override
        public AuditEvent record(AuditEventCommand command) {
            if (failNextRecord.compareAndSet(true, false)) {
                throw new IllegalStateException("audit storage failure");
            }
            return super.record(command);
        }

        void failNextRecord() {
            failNextRecord.set(true);
        }

        void resetFailureInjection() {
            failNextRecord.set(false);
        }
    }

    private record Fixture(
        AppUser actor,
        Region region
    ) {
    }
}
