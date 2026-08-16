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
import java.util.concurrent.atomic.AtomicLong;

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

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationHoldRequest;
import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationHoldResponse;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.reservation.service.CreateReservationHoldUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationConfirmationResult;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationConfirmationUseCase;
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
class SuspendContentConcurrencyMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final int FIRST_SESSION_CAPACITY = 10;
    private static final int SECOND_SESSION_CAPACITY = 8;
    private static final int FIRST_HOLD_QUANTITY = 2;
    private static final int SECOND_HOLD_QUANTITY = 3;
    private static final int LOCK_WAIT_CONFIRMATION_ATTEMPTS = 30;
    private static final long LOCK_WAIT_CONFIRMATION_INTERVAL_MILLIS = 100;

    private final SuspendContentUseCase suspendContentUseCase;
    private final CreateReservationHoldUseCase createReservationHoldUseCase;
    private final ReservationConfirmationUseCase reservationConfirmationUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentLogRepository contentLogRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    SuspendContentConcurrencyMySqlTest(
        SuspendContentUseCase suspendContentUseCase,
        CreateReservationHoldUseCase createReservationHoldUseCase,
        ReservationConfirmationUseCase reservationConfirmationUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        ContentLogRepository contentLogRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.suspendContentUseCase = suspendContentUseCase;
        this.createReservationHoldUseCase = createReservationHoldUseCase;
        this.reservationConfirmationUseCase = reservationConfirmationUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.contentLogRepository = contentLogRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.grantLockMonitoringPrivileges();
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
        registry.add("idempotency.lock-wait-timeout-seconds", () -> "3");
    }

    @Test
    @Timeout(15)
    void 동시_중단은_하나만_성공하고_로그_감사_홀드_정원을_중복_변경하지_않는다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<SuspensionAttempt> attempts;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<SuspensionAttempt> first = executorService.submit(
                () -> suspendAfterStart(fixture, ready, start)
            );
            Future<SuspensionAttempt> second = executorService.submit(
                () -> suspendAfterStart(fixture, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );
        }

        assertThat(attempts).filteredOn(SuspensionAttempt::isSuccessful).singleElement();
        assertThat(attempts)
            .filteredOn(attempt -> !attempt.isSuccessful())
            .extracting(SuspensionAttempt::errorCode)
            .containsExactly(ErrorCode.CONTENT_SUSPEND_CONFLICT);
        assertSuspensionCommitted(fixture);
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED, ContentLogStatus.SUSPENDED);
        assertContentAuditEvents(fixture.contentId(), 1, 1);
    }

    @Test
    @Timeout(15)
    void 중단이_먼저_커밋되면_경합한_신규_홀드_생성을_차단한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch suspensionChanged = new CountDownLatch(1);
        CountDownLatch holdCreationStarted = new CountDownLatch(1);
        AtomicLong holdCreationConnectionId = new AtomicLong();

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<SuspendContentResult> suspension = executorService.submit(() -> transactionTemplate.execute(status -> {
                SuspendContentResult result = suspend(fixture);
                suspensionChanged.countDown();
                await(holdCreationStarted);
                assertThat(awaitLockWait(holdCreationConnectionId.get())).isTrue();
                return result;
            }));
            assertThat(suspensionChanged.await(5, TimeUnit.SECONDS)).isTrue();

            Future<HoldCreationAttempt> holdCreation = executorService.submit(() -> createHoldInTrackedTransaction(
                fixture,
                holdCreationConnectionId,
                holdCreationStarted
            ));

            assertThat(suspension.get(10, TimeUnit.SECONDS).status()).isEqualTo(ContentStatus.SUSPENDED);
            assertThat(holdCreation.get(10, TimeUnit.SECONDS).errorCode())
                .isEqualTo(ErrorCode.RESERVATION_HOLD_CONFLICT);
        }

        assertSuspensionCommitted(fixture);
        assertThat(capacityHoldRepository.findAll()).hasSize(2);
    }

    @Test
    @Timeout(15)
    void 홀드_생성이_먼저_커밋되면_생성된_홀드까지_무효화하고_정원을_복구한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch holdCreated = new CountDownLatch(1);
        CountDownLatch suspensionStarted = new CountDownLatch(1);
        AtomicLong suspensionConnectionId = new AtomicLong();

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<CreateReservationHoldResponse> holdCreation = executorService.submit(
                () -> transactionTemplate.execute(status -> {
                    CreateReservationHoldResponse response = createHold(fixture);
                    holdCreated.countDown();
                    await(suspensionStarted);
                    assertThat(awaitLockWait(suspensionConnectionId.get())).isTrue();
                    return response;
                })
            );
            assertThat(holdCreated.await(5, TimeUnit.SECONDS)).isTrue();

            Future<SuspendContentResult> suspension = executorService.submit(() -> suspendInTrackedTransaction(
                fixture,
                suspensionConnectionId,
                suspensionStarted
            ));

            CreateReservationHoldResponse createdHold = holdCreation.get(10, TimeUnit.SECONDS);
            assertThat(suspension.get(10, TimeUnit.SECONDS).status()).isEqualTo(ContentStatus.SUSPENDED);
            assertThat(capacityHoldRepository.findById(Long.valueOf(createdHold.holdId())))
                .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.INVALIDATED));
        }

        assertSuspensionCommitted(fixture);
        assertThat(capacityHoldRepository.findAll()).hasSize(3);
    }

    @Test
    @Timeout(15)
    void 중단이_먼저_커밋되면_경합한_예약_확정을_거부하고_홀드_정원을_복구한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch suspensionChanged = new CountDownLatch(1);
        CountDownLatch confirmationStarted = new CountDownLatch(1);
        AtomicLong confirmationConnectionId = new AtomicLong();

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<SuspendContentResult> suspension = executorService.submit(() -> transactionTemplate.execute(status -> {
                SuspendContentResult result = suspend(fixture);
                suspensionChanged.countDown();
                await(confirmationStarted);
                assertThat(awaitLockWait(confirmationConnectionId.get())).isTrue();
                return result;
            }));
            assertThat(suspensionChanged.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ReservationConfirmationResult> confirmation = executorService.submit(
                () -> confirmInTrackedTransaction(
                    fixture,
                    confirmationConnectionId,
                    confirmationStarted,
                    "suspension-first-"
                )
            );

            assertThat(suspension.get(10, TimeUnit.SECONDS).status()).isEqualTo(ContentStatus.SUSPENDED);
            assertThat(confirmation.get(10, TimeUnit.SECONDS).errorCode())
                .isEqualTo(ErrorCode.RESERVATION_CONFIRM_CONFLICT);
        }

        assertSuspensionCommitted(fixture);
        assertThat(reservationRepository.findAll())
            .noneMatch(reservation -> reservation.getCapacityHold().getHoldId().equals(fixture.firstHoldId()));
    }

    @Test
    @Timeout(15)
    void 예약_확정이_먼저_커밋되면_소비_홀드와_확정_예약을_유지한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch reservationConfirmed = new CountDownLatch(1);
        CountDownLatch suspensionStarted = new CountDownLatch(1);
        AtomicLong suspensionConnectionId = new AtomicLong();

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<ReservationConfirmationResult> confirmation = executorService.submit(
                () -> transactionTemplate.execute(status -> {
                    ReservationConfirmationResult result = confirm(fixture, "confirmation-first-");
                    reservationConfirmed.countDown();
                    await(suspensionStarted);
                    assertThat(awaitLockWait(suspensionConnectionId.get())).isTrue();
                    return result;
                })
            );
            assertThat(reservationConfirmed.await(5, TimeUnit.SECONDS)).isTrue();

            Future<SuspendContentResult> suspension = executorService.submit(() -> suspendInTrackedTransaction(
                fixture,
                suspensionConnectionId,
                suspensionStarted
            ));

            ReservationConfirmationResult confirmationResult = confirmation.get(10, TimeUnit.SECONDS);
            assertThat(confirmationResult.isSuccessful()).isTrue();
            assertThat(suspension.get(10, TimeUnit.SECONDS).status()).isEqualTo(ContentStatus.SUSPENDED);
            assertThat(capacityHoldRepository.findById(fixture.firstHoldId()))
                .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED));
            assertThat(reservationRepository.findById(
                Long.valueOf(confirmationResult.response().reservationId())
            )).hasValueSatisfying(reservation ->
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED)
            );
        }

        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.SUSPENDED));
        assertThat(contentSessionRepository.findById(fixture.firstSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity())
                .isEqualTo(FIRST_SESSION_CAPACITY - FIRST_HOLD_QUANTITY));
        assertThat(contentSessionRepository.findById(fixture.secondSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity())
                .isEqualTo(SECOND_SESSION_CAPACITY));
        assertThat(capacityHoldRepository.findById(fixture.secondHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.INVALIDATED));
    }

    private SuspensionAttempt suspendAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            return new SuspensionAttempt(suspend(fixture), null);
        } catch (BusinessException exception) {
            return new SuspensionAttempt(null, exception.getErrorCode());
        }
    }

    private SuspendContentResult suspend(Fixture fixture) {
        return suspendContentUseCase.suspend(
            fixture.adminId(),
            fixture.contentId(),
            "기상 악화",
            UUID.randomUUID()
        );
    }

    private SuspendContentResult suspendInTrackedTransaction(
        Fixture fixture,
        AtomicLong connectionId,
        CountDownLatch started
    ) {
        return transactionTemplate.execute(status -> {
            connectionId.set(findCurrentConnectionId());
            started.countDown();
            return suspend(fixture);
        });
    }

    private HoldCreationAttempt createHoldInTrackedTransaction(
        Fixture fixture,
        AtomicLong connectionId,
        CountDownLatch started
    ) {
        try {
            CreateReservationHoldResponse response = transactionTemplate.execute(status -> {
                connectionId.set(findCurrentConnectionId());
                started.countDown();
                return createHold(fixture);
            });
            return new HoldCreationAttempt(response, null);
        } catch (BusinessException exception) {
            return new HoldCreationAttempt(null, exception.getErrorCode());
        }
    }

    private CreateReservationHoldResponse createHold(Fixture fixture) {
        return createReservationHoldUseCase.create(
            fixture.holdCreatorUserId(),
            new CreateReservationHoldRequest(fixture.firstSessionId().toString(), 1)
        );
    }

    private ReservationConfirmationResult confirmInTrackedTransaction(
        Fixture fixture,
        AtomicLong connectionId,
        CountDownLatch started,
        String idempotencyKeyPrefix
    ) {
        return transactionTemplate.execute(status -> {
            connectionId.set(findCurrentConnectionId());
            started.countDown();
            return confirm(fixture, idempotencyKeyPrefix);
        });
    }

    private ReservationConfirmationResult confirm(Fixture fixture, String idempotencyKeyPrefix) {
        return reservationConfirmationUseCase.confirm(
            fixture.confirmingUserId(),
            fixture.firstHoldId().toString(),
            idempotencyKeyPrefix + System.nanoTime(),
            UUID.randomUUID()
        );
    }

    private boolean awaitLockWait(long requestingConnectionId) {
        for (int attempt = 0; attempt < LOCK_WAIT_CONFIRMATION_ATTEMPTS; attempt++) {
            Integer waitingLockCount = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM performance_schema.data_lock_waits AS lock_wait
                    JOIN performance_schema.threads AS requesting_thread
                        ON requesting_thread.thread_id = lock_wait.requesting_thread_id
                    WHERE requesting_thread.processlist_id = ?
                    """,
                Integer.class,
                requestingConnectionId
            );
            if (waitingLockCount != null && waitingLockCount > 0) {
                return true;
            }
            awaitLockWaitConfirmationInterval();
        }
        return false;
    }

    private long findCurrentConnectionId() {
        Long connectionId = jdbcTemplate.queryForObject("SELECT CONNECTION_ID()", Long.class);
        if (connectionId == null) {
            throw new IllegalStateException("MySQL connection id does not exist");
        }
        return connectionId;
    }

    private void assertSuspensionCommitted(Fixture fixture) {
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.SUSPENDED));
        assertThat(capacityHoldRepository.findById(fixture.firstHoldId()))
            .hasValueSatisfying(hold -> assertInvalidated(hold));
        assertThat(capacityHoldRepository.findById(fixture.secondHoldId()))
            .hasValueSatisfying(hold -> assertInvalidated(hold));
        assertThat(contentSessionRepository.findById(fixture.firstSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity())
                .isEqualTo(FIRST_SESSION_CAPACITY));
        assertThat(contentSessionRepository.findById(fixture.secondSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity())
                .isEqualTo(SECOND_SESSION_CAPACITY));
    }

    private void assertInvalidated(CapacityHold capacityHold) {
        assertThat(capacityHold.getStatus()).isEqualTo(CapacityHoldStatus.INVALIDATED);
        assertThat(capacityHold.getInvalidationReason()).isEqualTo("CONTENT_SUSPENDED");
        assertThat(capacityHold.getTerminalAt()).isNotNull();
        assertThat(capacityHold.getCapacityReleasedAt()).isNotNull();
    }

    private void assertContentAuditEvents(Long contentId, int successCount, int failureCount) {
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.CONTENT)
            .filteredOn(event -> event.getTargetId().equals(contentId))
            .filteredOn(event -> event.getResult() == AuditEventResult.SUCCESS)
            .hasSize(successCount);
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.CONTENT)
            .filteredOn(event -> event.getTargetId().equals(contentId))
            .filteredOn(event -> event.getResult() == AuditEventResult.FAILURE)
            .hasSize(failureCount);
        long linkedSuccessCount = auditEventRepository.findAll().stream()
            .filter(event -> event.getTargetType() == AuditEventTargetType.CONTENT)
            .filter(event -> event.getTargetId().equals(contentId))
            .filter(event -> event.getResult() == AuditEventResult.SUCCESS)
            .filter(event -> auditEventActorLinkRepository.existsById(event.getAuditEventId()))
            .count();
        long linkedFailureCount = auditEventRepository.findAll().stream()
            .filter(event -> event.getTargetType() == AuditEventTargetType.CONTENT)
            .filter(event -> event.getTargetId().equals(contentId))
            .filter(event -> event.getResult() == AuditEventResult.FAILURE)
            .filter(event -> auditEventActorLinkRepository.existsById(event.getAuditEventId()))
            .count();
        assertThat(linkedSuccessCount).isEqualTo(successCount);
        assertThat(linkedFailureCount).isZero();
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.save(new Region("SUSPEND-" + suffix, "김해시", true));
            AppUser admin = saveUser("admin-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
            AppUser operator = saveUser("operator-" + suffix);
            AppUser confirmingUser = saveUser("confirming-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                confirmingUser,
                UserRole.VISITOR,
                null
            ));
            AppUser secondHoldUser = saveUser("second-hold-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                secondHoldUser,
                UserRole.VISITOR,
                null
            ));
            AppUser holdCreator = saveUser("hold-creator-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(holdCreator, UserRole.VISITOR, null));

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
            ContentSession firstSession = createSession(
                content,
                region,
                admin,
                now.plusSeconds(3_600),
                FIRST_SESSION_CAPACITY,
                now
            );
            ContentSession secondSession = createSession(
                content,
                region,
                admin,
                now.plusSeconds(7_200),
                SECOND_SESSION_CAPACITY,
                now
            );
            contentSessionRepository.saveAllAndFlush(List.of(firstSession, secondSession));

            CapacityHold firstHold = createActiveHold(
                region,
                firstSession,
                confirmingUser,
                FIRST_HOLD_QUANTITY,
                now
            );
            CapacityHold secondHold = createActiveHold(
                region,
                secondSession,
                secondHoldUser,
                SECOND_HOLD_QUANTITY,
                now
            );
            return new Fixture(
                admin.getUserId(),
                holdCreator.getUserId(),
                confirmingUser.getUserId(),
                content.getContentId(),
                firstSession.getSessionId(),
                secondSession.getSessionId(),
                firstHold.getHoldId(),
                secondHold.getHoldId()
            );
        });
    }

    private AppUser saveUser(String identifierPrefix) {
        return appUserRepository.save(new AppUser(
            identifierPrefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private ContentSession createSession(
        Content content,
        Region region,
        AppUser admin,
        Instant startsAt,
        int capacity,
        Instant approvedAt
    ) {
        ContentSession contentSession = new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(3_600),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(1_800),
            capacity
        );
        contentSession.approve(admin, approvedAt);
        return contentSession;
    }

    private CapacityHold createActiveHold(
        Region region,
        ContentSession contentSession,
        AppUser user,
        int quantity,
        Instant createdAt
    ) {
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = remaining_capacity - ? WHERE session_id = ?",
            quantity,
            contentSession.getSessionId()
        );
        return capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            contentSession,
            user,
            quantity,
            CapacityHoldStatus.ACTIVE,
            createdAt.plusSeconds(600),
            null,
            null,
            null,
            createdAt
        ));
    }

    private void awaitLockWaitConfirmationInterval() {
        try {
            TimeUnit.MILLISECONDS.sleep(LOCK_WAIT_CONFIRMATION_INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("lock wait confirmation was interrupted", exception);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrency test synchronization timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrency test synchronization interrupted", exception);
        }
    }

    private record Fixture(
        Long adminId,
        Long holdCreatorUserId,
        Long confirmingUserId,
        Long contentId,
        Long firstSessionId,
        Long secondSessionId,
        Long firstHoldId,
        Long secondHoldId
    ) {
    }

    private record SuspensionAttempt(
        SuspendContentResult result,
        ErrorCode errorCode
    ) {

        private boolean isSuccessful() {
            return result != null;
        }
    }

    private record HoldCreationAttempt(
        CreateReservationHoldResponse response,
        ErrorCode errorCode
    ) {
    }
}
