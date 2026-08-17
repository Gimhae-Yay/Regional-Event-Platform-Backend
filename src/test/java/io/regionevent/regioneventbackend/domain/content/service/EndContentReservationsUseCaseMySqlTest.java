package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionInvalidationReason;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
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

@SpringBootTest(properties = {
    "reservation.hold-termination.initial-delay=PT24H",
    "reservation.no-show-completion.initial-delay=PT24H"
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EndContentReservationsUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final int SESSION_CAPACITY = 10;

    private final EndContentReservationsUseCase endContentReservationsUseCase;
    private final ContentRepository contentRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentLogRepository contentLogRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    EndContentReservationsUseCaseMySqlTest(
        EndContentReservationsUseCase endContentReservationsUseCase,
        ContentRepository contentRepository,
        ContentRevisionRepository contentRevisionRepository,
        ContentSessionRepository contentSessionRepository,
        ContentLogRepository contentLogRepository,
        CapacityHoldRepository capacityHoldRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.endContentReservationsUseCase = endContentReservationsUseCase;
        this.contentRepository = contentRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.contentLogRepository = contentLogRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.jdbcTemplate = jdbcTemplate;
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void 반려된_회차가_있어도_모든_회차가_종결되면_콘텐츠를_종료한다() {
        Fixture fixture = createFixture();
        saveRejectedSession(fixture);

        EndContentReservationsResult result = endContentReservationsUseCase.end(
            fixture.adminId(),
            fixture.contentId(),
            UUID.randomUUID()
        );

        assertThat(result.status()).isEqualTo(ContentStatus.ENDED);
    }

    @Test
    void 종료는_활성홀드를_무효화하고_정원을_한번만_복구한다() {
        Fixture fixture = createFixture();

        EndContentReservationsResult result = endContentReservationsUseCase.end(
            fixture.adminId(),
            fixture.contentId(),
            UUID.randomUUID()
        );

        assertThat(result.status()).isEqualTo(ContentStatus.ENDED);
        ContentLog endedLog = contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId())
            .getLast();
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.ENDED));
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED, ContentLogStatus.ENDED);
        assertThat(capacityHoldRepository.findById(fixture.firstHoldId()))
            .hasValueSatisfying(this::assertInvalidated);
        assertThat(capacityHoldRepository.findById(fixture.secondHoldId()))
            .hasValueSatisfying(this::assertInvalidated);
        assertThat(contentSessionRepository.findById(fixture.firstSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
        assertThat(contentSessionRepository.findById(fixture.secondSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> auditEvent.getTargetType() == AuditEventTargetType.CONTENT)
            .filteredOn(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT);
                assertThat(auditEvent.getPreviousState()).isEqualTo("PUBLISHED");
                assertThat(auditEvent.getNextState()).isEqualTo("ENDED");
                assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                    .hasValueSatisfying(actorLink ->
                        assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.adminId())
                    );
                assertThat(auditEvent.getOccurredAt()).isEqualTo(result.endedAt());
            });
        assertThat(endedLog.getDate()).isEqualTo(result.endedAt());
    }

    @Test
    void 시스템_종료는_종결_회차를_후보로_조회해_SYSTEM_감사와_함께_종료한다() {
        Fixture fixture = createFixture();

        assertThat(endContentReservationsUseCase.findAutoEndCandidateIds())
            .contains(fixture.contentId());

        endContentReservationsUseCase.endBySystem(fixture.contentId(), UUID.randomUUID());

        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.ENDED));
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> auditEvent.getTargetType() == AuditEventTargetType.CONTENT)
            .filteredOn(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getActorKind()).isEqualTo("SYSTEM");
                assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId())).isEmpty();
            });
    }

    @Test
    void 수동_종료는_활성_수정본을_처리자와_감사_이력으로_무효화한다() {
        Fixture fixture = createFixture();
        ContentRevision revision = saveEditRequestedRevision(fixture);

        EndContentReservationsResult result = endContentReservationsUseCase.end(
            fixture.adminId(),
            fixture.contentId(),
            UUID.randomUUID()
        );

        assertThat(contentRevisionRepository.findById(revision.getContentRevisionId()))
            .hasValueSatisfying(actual -> {
                assertThat(actual.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_INVALIDATED);
                assertThat(actual.getInvalidatedAt()).isEqualTo(result.endedAt());
                assertThat(actual.getInvalidationReason())
                    .isEqualTo(ContentRevisionInvalidationReason.CONTENT_ENDED);
            });
        assertThat(jdbcTemplate.queryForObject(
            "SELECT invalidated_by_user_id FROM content_revision WHERE content_revision_id = ?",
            Long.class,
            revision.getContentRevisionId()
        )).isEqualTo(fixture.adminId());
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> auditEvent.getTargetType() == AuditEventTargetType.CONTENT)
            .filteredOn(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .filteredOn(auditEvent -> "EDIT_REQUESTED".equals(auditEvent.getPreviousState()))
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getNextState()).isEqualTo("EDIT_INVALIDATED");
                assertThat(auditEvent.getReasonCode()).isEqualTo("CONTENT_ENDED");
                assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                    .hasValueSatisfying(actorLink ->
                        assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.adminId())
                    );
            });
    }

    @Test
    void 시스템_종료는_활성_수정본을_처리자_없이_무효화한다() {
        Fixture fixture = createFixture();
        ContentRevision revision = saveEditRequestedRevision(fixture);

        EndContentReservationsSystemResult result = endContentReservationsUseCase.endBySystem(
            fixture.contentId(),
            UUID.randomUUID()
        );

        assertThat(result.status()).isEqualTo(EndContentReservationsSystemResult.Status.ENDED);
        assertThat(contentRevisionRepository.findById(revision.getContentRevisionId()))
            .hasValueSatisfying(actual -> {
                assertThat(actual.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_INVALIDATED);
                assertThat(actual.getInvalidatedAt()).isNotNull();
                assertThat(actual.getInvalidationReason())
                    .isEqualTo(ContentRevisionInvalidationReason.CONTENT_ENDED);
            });
        assertThat(jdbcTemplate.queryForObject(
            "SELECT invalidated_by_user_id FROM content_revision WHERE content_revision_id = ?",
            Long.class,
            revision.getContentRevisionId()
        )).isNull();
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> auditEvent.getTargetType() == AuditEventTargetType.CONTENT)
            .filteredOn(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .filteredOn(auditEvent -> "EDIT_REQUESTED".equals(auditEvent.getPreviousState()))
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getActorKind()).isEqualTo("SYSTEM");
                assertThat(auditEvent.getNextState()).isEqualTo("EDIT_INVALIDATED");
                assertThat(auditEvent.getReasonCode()).isEqualTo("CONTENT_ENDED");
                assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId())).isEmpty();
            });
    }

    @Test
    void 오래된_시스템_후보에_미종결_회차가_추가되면_변경과_실패_감사_없이_건너뛴다() {
        Fixture fixture = createFixture();
        transactionTemplate.executeWithoutResult(status -> {
            Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
            contentSessionRepository.saveAndFlush(newSession(
                content,
                content.getRegion(),
                Instant.now().plusSeconds(86_400)
            ));
        });

        endContentReservationsUseCase.endBySystem(fixture.contentId(), UUID.randomUUID());

        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED));
        assertNoEndSideEffects(fixture);
    }

    @Test
    @Timeout(10)
    void 동시_종료_요청에서도_로그와_감사와_정원복구는_한번만_발생한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<EndContentReservationsResult> results;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<EndContentReservationsResult> first = executorService.submit(
                () -> endAfterStart(fixture, ready, start)
            );
            Future<EndContentReservationsResult> second = executorService.submit(
                () -> endAfterStart(fixture, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }

        assertThat(results).extracting(EndContentReservationsResult::status)
            .containsOnly(ContentStatus.ENDED);
        assertThat(results).extracting(EndContentReservationsResult::endedAt)
            .containsOnly(results.getFirst().endedAt());
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED, ContentLogStatus.ENDED);
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> auditEvent.getTargetType() == AuditEventTargetType.CONTENT)
            .filteredOn(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .hasSize(1);
        assertThat(capacityHoldRepository.findById(fixture.firstHoldId()))
            .hasValueSatisfying(this::assertInvalidated);
        assertThat(contentSessionRepository.findById(fixture.firstSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
    }

    @Test
    @Timeout(10)
    void 동시_시스템_종료에서도_로그와_감사와_정원복구는_한번만_발생한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<EndContentReservationsSystemResult> first = executorService.submit(
                () -> endBySystemAfterStart(fixture, ready, start)
            );
            Future<EndContentReservationsSystemResult> second = executorService.submit(
                () -> endBySystemAfterStart(fixture, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                .extracting(EndContentReservationsSystemResult::status)
                .containsExactlyInAnyOrder(
                    EndContentReservationsSystemResult.Status.ENDED,
                    EndContentReservationsSystemResult.Status.SKIPPED
                );
        }

        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED, ContentLogStatus.ENDED);
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> auditEvent.getTargetType() == AuditEventTargetType.CONTENT)
            .filteredOn(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .hasSize(1);
        assertThat(capacityHoldRepository.findById(fixture.firstHoldId()))
            .hasValueSatisfying(this::assertInvalidated);
        assertThat(contentSessionRepository.findById(fixture.firstSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
    }

    @Test
    @Timeout(15)
    void 시스템_종료가_콘텐츠_잠금을_먼저_대기하면_수동_종료는_기존_종료_결과를_반환한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch contentLocked = new CountDownLatch(1);
        CountDownLatch releaseContentLock = new CountDownLatch(1);

        EndContentReservationsSystemResult systemResult;
        EndContentReservationsResult regionAdminResult;
        try (ExecutorService executorService = Executors.newFixedThreadPool(3)) {
            Future<?> lockHolder = executorService.submit(
                () -> holdContentLock(fixture.contentId(), contentLocked, releaseContentLock)
            );
            assertThat(contentLocked.await(3, TimeUnit.SECONDS)).isTrue();

            Future<EndContentReservationsSystemResult> systemEnd = executorService.submit(
                () -> endContentReservationsUseCase.endBySystem(fixture.contentId(), UUID.randomUUID())
            );
            Future<EndContentReservationsResult> regionAdminEnd;
            try {
                assertThat(awaitContentLockWaitCount(1)).isTrue();
                regionAdminEnd = executorService.submit(() -> endContentReservationsUseCase.endByRegionAdmin(
                    fixture.adminId(),
                    fixture.contentId(),
                    UUID.randomUUID()
                ));
                assertThat(awaitContentLockWaitCount(2)).isTrue();
            } finally {
                releaseContentLock.countDown();
            }

            lockHolder.get(5, TimeUnit.SECONDS);
            systemResult = systemEnd.get(5, TimeUnit.SECONDS);
            regionAdminResult = regionAdminEnd.get(5, TimeUnit.SECONDS);
        }

        ContentLog endedLog = assertSingleSuccessfulEndSideEffects(fixture);
        assertThat(systemResult.status()).isEqualTo(EndContentReservationsSystemResult.Status.ENDED);
        assertThat(regionAdminResult.status()).isEqualTo(ContentStatus.ENDED);
        assertThat(regionAdminResult.endedAt()).isEqualTo(endedLog.getDate());
    }

    @Test
    @Timeout(15)
    void 수동_종료가_콘텐츠_잠금을_먼저_대기하면_시스템_종료는_건너뛴다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch contentLocked = new CountDownLatch(1);
        CountDownLatch releaseContentLock = new CountDownLatch(1);

        EndContentReservationsResult regionAdminResult;
        EndContentReservationsSystemResult systemResult;
        try (ExecutorService executorService = Executors.newFixedThreadPool(3)) {
            Future<?> lockHolder = executorService.submit(
                () -> holdContentLock(fixture.contentId(), contentLocked, releaseContentLock)
            );
            assertThat(contentLocked.await(3, TimeUnit.SECONDS)).isTrue();

            Future<EndContentReservationsResult> regionAdminEnd = executorService.submit(
                () -> endContentReservationsUseCase.endByRegionAdmin(
                    fixture.adminId(),
                    fixture.contentId(),
                    UUID.randomUUID()
                )
            );
            Future<EndContentReservationsSystemResult> systemEnd;
            try {
                assertThat(awaitContentLockWaitCount(1)).isTrue();
                systemEnd = executorService.submit(
                    () -> endContentReservationsUseCase.endBySystem(fixture.contentId(), UUID.randomUUID())
                );
                assertThat(awaitContentLockWaitCount(2)).isTrue();
            } finally {
                releaseContentLock.countDown();
            }

            lockHolder.get(5, TimeUnit.SECONDS);
            regionAdminResult = regionAdminEnd.get(5, TimeUnit.SECONDS);
            systemResult = systemEnd.get(5, TimeUnit.SECONDS);
        }

        ContentLog endedLog = assertSingleSuccessfulEndSideEffects(fixture);
        assertThat(regionAdminResult.status()).isEqualTo(ContentStatus.ENDED);
        assertThat(regionAdminResult.endedAt()).isEqualTo(endedLog.getDate());
        assertThat(systemResult.status()).isEqualTo(EndContentReservationsSystemResult.Status.SKIPPED);
    }

    @Test
    void 종료된_콘텐츠를_순차_재시도하면_기존_종료_결과만_반환하고_종료_부수_효과를_중복_생성하지_않는다() {
        Fixture fixture = createFixture();

        EndContentReservationsResult firstResult = endContentReservationsUseCase.end(
            fixture.adminId(),
            fixture.contentId(),
            UUID.randomUUID()
        );
        EndContentReservationsResult retryResult = endContentReservationsUseCase.end(
            fixture.adminId(),
            fixture.contentId(),
            UUID.randomUUID()
        );

        assertThat(retryResult).isEqualTo(firstResult);
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED, ContentLogStatus.ENDED);
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> auditEvent.getTargetType() == AuditEventTargetType.CONTENT)
            .filteredOn(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .hasSize(1);
        assertThat(capacityHoldRepository.findById(fixture.firstHoldId()))
            .hasValueSatisfying(this::assertInvalidated);
        assertThat(capacityHoldRepository.findById(fixture.secondHoldId()))
            .hasValueSatisfying(this::assertInvalidated);
        assertThat(contentSessionRepository.findById(fixture.firstSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
        assertThat(contentSessionRepository.findById(fixture.secondSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
    }

    @Test
    void 종료_이력이_여러건인_종료_콘텐츠_재시도는_최신_종료_결과를_반환하고_부수_효과를_추가하지_않는다() {
        Fixture fixture = createFixture();
        EndContentReservationsResult firstResult = endContentReservationsUseCase.end(
            fixture.adminId(),
            fixture.contentId(),
            UUID.randomUUID()
        );
        Instant latestEndedAt = firstResult.endedAt().plusSeconds(1);
        transactionTemplate.executeWithoutResult(status -> {
            Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
            AppUser admin = appUserRepository.findById(fixture.adminId()).orElseThrow();
            contentLogRepository.saveAndFlush(new ContentLog(
                content,
                admin,
                ContentLogStatus.ENDED,
                null,
                latestEndedAt
            ));
        });
        CapacityHold firstHoldBeforeRetry = capacityHoldRepository.findById(fixture.firstHoldId()).orElseThrow();
        CapacityHold secondHoldBeforeRetry = capacityHoldRepository.findById(fixture.secondHoldId()).orElseThrow();

        EndContentReservationsResult retryResult = endContentReservationsUseCase.end(
            fixture.adminId(),
            fixture.contentId(),
            UUID.randomUUID()
        );

        assertThat(retryResult.status()).isEqualTo(ContentStatus.ENDED);
        assertThat(retryResult.endedAt()).isEqualTo(latestEndedAt);
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED, ContentLogStatus.ENDED, ContentLogStatus.ENDED);
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> auditEvent.getTargetType() == AuditEventTargetType.CONTENT)
            .filteredOn(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .hasSize(1);
        assertThat(capacityHoldRepository.findById(fixture.firstHoldId()))
            .hasValueSatisfying(hold -> assertUnchangedInvalidated(hold, firstHoldBeforeRetry));
        assertThat(capacityHoldRepository.findById(fixture.secondHoldId()))
            .hasValueSatisfying(hold -> assertUnchangedInvalidated(hold, secondHoldBeforeRetry));
        assertThat(contentSessionRepository.findById(fixture.firstSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
        assertThat(contentSessionRepository.findById(fixture.secondSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
    }

    @Test
    @Timeout(10)
    void 다른_상태_전이가_먼저_커밋되면_종료_요청은_충돌하고_종료_부수_효과를_생성하지_않는다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch stateTransitionApplied = new CountDownLatch(1);
        CountDownLatch allowStateTransitionCommit = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Integer> stateTransition = executorService.submit(
                () -> suspendAfterStart(
                    fixture.contentId(),
                    stateTransitionApplied,
                    allowStateTransitionCommit
                )
            );
            assertThat(stateTransitionApplied.await(3, TimeUnit.SECONDS)).isTrue();
            Future<Attempt> endAttempt = executorService.submit(
                () -> endExpectingConflict(fixture)
            );
            try {
                assertThat(awaitEndRequestLockWait()).isTrue();
            } finally {
                allowStateTransitionCommit.countDown();
            }

            assertThat(stateTransition.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(endAttempt.get(5, TimeUnit.SECONDS).errorCode())
                .isEqualTo(ErrorCode.CONTENT_END_CONFLICT);
        }

        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.SUSPENDED));
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED);
        assertFailureAuditOnly(fixture);
    }

    private EndContentReservationsResult endAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return endContentReservationsUseCase.end(
            fixture.adminId(),
            fixture.contentId(),
            UUID.randomUUID()
        );
    }

    private EndContentReservationsSystemResult endBySystemAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return endContentReservationsUseCase.endBySystem(fixture.contentId(), UUID.randomUUID());
    }

    private Attempt endExpectingConflict(Fixture fixture) {
        try {
            endContentReservationsUseCase.end(
                fixture.adminId(),
                fixture.contentId(),
                UUID.randomUUID()
            );
            return new Attempt(null);
        } catch (BusinessException exception) {
            return new Attempt(exception.getErrorCode());
        }
    }

    private int suspendAfterStart(
        Long contentId,
        CountDownLatch applied,
        CountDownLatch allowCommit
    ) {
        return transactionTemplate.execute(status -> {
            int updatedCount = contentRepository.updateStatusIfExpected(
                contentId,
                ContentStatus.PUBLISHED,
                ContentStatus.SUSPENDED,
                Instant.now()
            );
            applied.countDown();
            await(allowCommit);
            return updatedCount;
        });
    }

    private void holdContentLock(
        Long contentId,
        CountDownLatch contentLocked,
        CountDownLatch releaseContentLock
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            contentRepository.findByContentIdAndDeletedAtIsNull(contentId).orElseThrow();
            contentLocked.countDown();
            await(releaseContentLock);
        });
    }

    private boolean awaitContentLockWaitCount(int expectedCount) {
        for (int attempt = 0; attempt < 30; attempt++) {
            Integer waitingRequestCount = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM information_schema.processlist
                    WHERE id <> CONNECTION_ID()
                        AND db = DATABASE()
                        AND command = 'Query'
                        AND LOWER(info) LIKE '%for update%'
                    """,
                Integer.class
            );
            if (waitingRequestCount != null && waitingRequestCount >= expectedCount) {
                return true;
            }
            awaitLockWaitInterval();
        }
        return false;
    }

    private boolean awaitEndRequestLockWait() {
        for (int attempt = 0; attempt < 30; attempt++) {
            Integer waitingEndRequestCount = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM information_schema.processlist
                    WHERE id <> CONNECTION_ID()
                        AND db = DATABASE()
                        AND command = 'Query'
                        AND LOWER(info) LIKE '%for update%'
                    """,
                Integer.class
            );
            if (waitingEndRequestCount != null && waitingEndRequestCount > 0) {
                return true;
            }
            awaitLockWaitInterval();
        }
        return false;
    }

    private void awaitLockWaitInterval() {
        try {
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("lock wait confirmation was interrupted", exception);
        }
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.save(new Region("R" + suffix, "김해시", true));
            AppUser admin = saveUser("admin-" + suffix, AppUserStatus.ACTIVE);
            userRoleAssignmentRepository.save(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
            AppUser operator = saveUser("operator-" + suffix, AppUserStatus.ACTIVE);
            AppUser visitor = saveUser("visitor-" + suffix, AppUserStatus.ACTIVE);
            Content content = contentRepository.save(new Content(
                region,
                operator,
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
            contentLogRepository.save(new ContentLog(
                content,
                operator,
                ContentLogStatus.PUBLISHED,
                null,
                now.minusSeconds(86_400)
            ));
            ContentSession firstSession = saveCompletedSession(content, region, admin, now.plusSeconds(3_600));
            ContentSession secondSession = saveCancelledSession(content, region, admin, now.plusSeconds(10_800));
            CapacityHold firstHold = saveActiveHold(region, firstSession, visitor, 2, now);
            CapacityHold secondHold = saveActiveHold(region, secondSession, visitor, 1, now);
            return new Fixture(
                admin.getUserId(),
                region.getRegionId(),
                content.getContentId(),
                firstSession.getSessionId(),
                secondSession.getSessionId(),
                firstHold.getHoldId(),
                secondHold.getHoldId()
            );
        });
    }

    private ContentSession saveCompletedSession(
        Content content,
        Region region,
        AppUser admin,
        Instant startsAt
    ) {
        ContentSession session = newSession(content, region, startsAt);
        session.approve(admin, startsAt.minusSeconds(3_600));
        session.complete(startsAt.minusSeconds(1_800));
        return contentSessionRepository.save(session);
    }

    private ContentSession saveCancelledSession(
        Content content,
        Region region,
        AppUser admin,
        Instant startsAt
    ) {
        ContentSession session = newSession(content, region, startsAt);
        session.approve(admin, startsAt.minusSeconds(3_600));
        session.cancel(admin, startsAt.minusSeconds(1_800), "정상 종료 전 회차 취소");
        return contentSessionRepository.save(session);
    }

    private void saveRejectedSession(Fixture fixture) {
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        Region region = regionRepository.findById(fixture.regionId()).orElseThrow();
        AppUser admin = appUserRepository.findById(fixture.adminId()).orElseThrow();
        Instant startsAt = Instant.now().plusSeconds(14_400);
        ContentSession session = newSession(content, region, startsAt);
        session.reject(admin, startsAt.minusSeconds(3_600), "추가 회차 반려");
        contentSessionRepository.save(session);
    }

    private ContentRevision saveEditRequestedRevision(Fixture fixture) {
        return transactionTemplate.execute(status -> {
            Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
            return contentRevisionRepository.saveAndFlush(new ContentRevision(
                content,
                1,
                content.getVersionNo(),
                content.getOperator(),
                ContentRevisionStatus.EDIT_REQUESTED,
                "수정 제목",
                "수정 설명",
                "김해문화의전당",
                "매일 10:00~18:00",
                "055-123-4567",
                "안전 수칙",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소",
                0,
                null,
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null
            ));
        });
    }

    private ContentSession newSession(
        Content content,
        Region region,
        Instant startsAt
    ) {
        return new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(10_800),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(9_000),
            SESSION_CAPACITY
        );
    }

    private CapacityHold saveActiveHold(
        Region region,
        ContentSession session,
        AppUser visitor,
        int quantity,
        Instant now
    ) {
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = remaining_capacity - ? WHERE session_id = ?",
            quantity,
            session.getSessionId()
        );
        return capacityHoldRepository.save(new CapacityHold(
            region,
            session,
            visitor,
            quantity,
            CapacityHoldStatus.ACTIVE,
            now.plusSeconds(600),
            null,
            null,
            null,
            now
        ));
    }

    private AppUser saveUser(String identifierPrefix, AppUserStatus status) {
        return appUserRepository.save(new AppUser(
            identifierPrefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            status
        ));
    }

    private void assertInvalidated(CapacityHold capacityHold) {
        assertThat(capacityHold.getStatus()).isEqualTo(CapacityHoldStatus.INVALIDATED);
        assertThat(capacityHold.getInvalidationReason()).isEqualTo("CONTENT_ENDED");
        assertThat(capacityHold.getTerminalAt()).isNotNull();
        assertThat(capacityHold.getCapacityReleasedAt()).isNotNull();
    }

    private void assertUnchangedInvalidated(CapacityHold actual, CapacityHold beforeRetry) {
        assertInvalidated(actual);
        assertThat(actual.getTerminalAt()).isEqualTo(beforeRetry.getTerminalAt());
        assertThat(actual.getCapacityReleasedAt()).isEqualTo(beforeRetry.getCapacityReleasedAt());
    }

    private ContentLog assertSingleSuccessfulEndSideEffects(Fixture fixture) {
        List<ContentLog> contentLogs = contentLogRepository
            .findByContentContentIdOrderByDateAscIdAsc(fixture.contentId());
        assertThat(contentLogs)
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED, ContentLogStatus.ENDED);
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> auditEvent.getTargetType() == AuditEventTargetType.CONTENT)
            .filteredOn(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .singleElement()
            .satisfies(auditEvent -> assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS));
        assertThat(capacityHoldRepository.findById(fixture.firstHoldId()))
            .hasValueSatisfying(this::assertInvalidated);
        assertThat(capacityHoldRepository.findById(fixture.secondHoldId()))
            .hasValueSatisfying(this::assertInvalidated);
        assertThat(contentSessionRepository.findById(fixture.firstSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
        assertThat(contentSessionRepository.findById(fixture.secondSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
        return contentLogs.getLast();
    }

    private void assertNoEndSideEffects(Fixture fixture) {
        assertThat(auditEventRepository.findAll())
            .noneMatch(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()));
        assertThat(capacityHoldRepository.findById(fixture.firstHoldId()))
            .hasValueSatisfying(this::assertActive);
        assertThat(capacityHoldRepository.findById(fixture.secondHoldId()))
            .hasValueSatisfying(this::assertActive);
        assertThat(contentSessionRepository.findById(fixture.firstSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(8));
        assertThat(contentSessionRepository.findById(fixture.secondSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(9));
    }

    private void assertFailureAuditOnly(Fixture fixture) {
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> auditEvent.getTargetType() == AuditEventTargetType.CONTENT)
            .filteredOn(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
                assertThat(auditEvent.getReasonCode()).isEqualTo(ErrorCode.CONTENT_END_CONFLICT.name());
            });
        assertThat(capacityHoldRepository.findById(fixture.firstHoldId()))
            .hasValueSatisfying(this::assertActive);
        assertThat(capacityHoldRepository.findById(fixture.secondHoldId()))
            .hasValueSatisfying(this::assertActive);
    }

    private void assertActive(CapacityHold capacityHold) {
        assertThat(capacityHold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE);
        assertThat(capacityHold.getInvalidationReason()).isNull();
        assertThat(capacityHold.getTerminalAt()).isNull();
        assertThat(capacityHold.getCapacityReleasedAt()).isNull();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent content ending did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent content ending was interrupted", exception);
        }
    }

    private record Fixture(
        Long adminId,
        Long regionId,
        Long contentId,
        Long firstSessionId,
        Long secondSessionId,
        Long firstHoldId,
        Long secondHoldId
    ) {
    }

    private record Attempt(ErrorCode errorCode) {
    }
}
