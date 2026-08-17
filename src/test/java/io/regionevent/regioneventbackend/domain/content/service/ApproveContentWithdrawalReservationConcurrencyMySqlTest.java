package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentWithdrawalRequestRepository;
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

@SpringBootTest(properties = {
    "reservation.hold-termination.initial-delay=PT24H",
    "reservation.no-show-completion.initial-delay=PT24H"
})
@Testcontainers(disabledWithoutDocker = true)
class ApproveContentWithdrawalReservationConcurrencyMySqlTest
    extends NonTransactionalMySqlTestSupport {

    private static final int SESSION_CAPACITY = 10;
    private static final int CONFIRMING_HOLD_QUANTITY = 2;
    private static final int SECOND_HOLD_QUANTITY = 3;
    private static final int LOCK_WAIT_CONFIRMATION_ATTEMPTS = 30;
    private static final long LOCK_WAIT_CONFIRMATION_INTERVAL_MILLIS = 100;

    private final ApproveContentWithdrawalUseCase approvalUseCase;
    private final CreateReservationHoldUseCase holdCreationUseCase;
    private final ReservationConfirmationUseCase confirmationUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository roleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentLogRepository contentLogRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentWithdrawalRequestRepository withdrawalRequestRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    ApproveContentWithdrawalReservationConcurrencyMySqlTest(
        ApproveContentWithdrawalUseCase approvalUseCase,
        CreateReservationHoldUseCase holdCreationUseCase,
        ReservationConfirmationUseCase confirmationUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository roleAssignmentRepository,
        ContentRepository contentRepository,
        ContentLogRepository contentLogRepository,
        ContentSessionRepository contentSessionRepository,
        ContentWithdrawalRequestRepository withdrawalRequestRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.approvalUseCase = approvalUseCase;
        this.holdCreationUseCase = holdCreationUseCase;
        this.confirmationUseCase = confirmationUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentLogRepository = contentLogRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
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
    void 승인이_먼저_커밋되면_경합한_신규_홀드_생성을_차단한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch approvalChanged = new CountDownLatch(1);
        CountDownLatch holdCreationStarted = new CountDownLatch(1);
        AtomicLong holdCreationConnectionId = new AtomicLong();

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<ApproveContentWithdrawalResult> approval = executorService.submit(
                () -> transactionTemplate.execute(status -> {
                    ApproveContentWithdrawalResult result = approve(fixture);
                    approvalChanged.countDown();
                    await(holdCreationStarted);
                    assertThat(awaitLockWait(holdCreationConnectionId.get())).isTrue();
                    return result;
                })
            );
            assertThat(approvalChanged.await(5, TimeUnit.SECONDS)).isTrue();

            Future<HoldCreationAttempt> holdCreation = executorService.submit(
                () -> createHoldInTrackedTransaction(
                    fixture,
                    holdCreationConnectionId,
                    holdCreationStarted
                )
            );

            assertThat(approval.get(10, TimeUnit.SECONDS).contentStatus())
                .isEqualTo(ContentStatus.WITHDRAWN);
            assertThat(holdCreation.get(10, TimeUnit.SECONDS).errorCode())
                .isEqualTo(ErrorCode.RESERVATION_HOLD_CONFLICT);
        }

        assertWithdrawalInvalidatedAllActiveHolds(fixture, SESSION_CAPACITY);
        assertThat(capacityHoldRepository.count()).isEqualTo(2);
    }

    @Test
    @Timeout(15)
    void 홀드_생성이_먼저_커밋되면_승인이_새_홀드까지_무효화하고_정원을_복구한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch holdCreated = new CountDownLatch(1);
        CountDownLatch approvalStarted = new CountDownLatch(1);
        AtomicLong approvalConnectionId = new AtomicLong();

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<CreateReservationHoldResponse> holdCreation = executorService.submit(
                () -> transactionTemplate.execute(status -> {
                    CreateReservationHoldResponse response = createHold(fixture);
                    holdCreated.countDown();
                    await(approvalStarted);
                    assertThat(awaitLockWait(approvalConnectionId.get())).isTrue();
                    return response;
                })
            );
            assertThat(holdCreated.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ApproveContentWithdrawalResult> approval = executorService.submit(
                () -> approveInTrackedTransaction(fixture, approvalConnectionId, approvalStarted)
            );

            CreateReservationHoldResponse createdHold = holdCreation.get(10, TimeUnit.SECONDS);
            assertThat(approval.get(10, TimeUnit.SECONDS).contentStatus())
                .isEqualTo(ContentStatus.WITHDRAWN);
            assertThat(capacityHoldRepository.findById(Long.valueOf(createdHold.holdId())))
                .hasValueSatisfying(this::assertInvalidated);
        }

        assertWithdrawalInvalidatedAllActiveHolds(fixture, SESSION_CAPACITY);
        assertThat(capacityHoldRepository.count()).isEqualTo(3);
    }

    @Test
    @Timeout(15)
    void 승인이_먼저_커밋되면_경합한_예약_확정을_거부한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch approvalChanged = new CountDownLatch(1);
        CountDownLatch confirmationStarted = new CountDownLatch(1);
        AtomicLong confirmationConnectionId = new AtomicLong();

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<ApproveContentWithdrawalResult> approval = executorService.submit(
                () -> transactionTemplate.execute(status -> {
                    ApproveContentWithdrawalResult result = approve(fixture);
                    approvalChanged.countDown();
                    await(confirmationStarted);
                    assertThat(awaitLockWait(confirmationConnectionId.get())).isTrue();
                    return result;
                })
            );
            assertThat(approvalChanged.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ReservationConfirmationResult> confirmation = executorService.submit(
                () -> confirmInTrackedTransaction(
                    fixture,
                    confirmationConnectionId,
                    confirmationStarted,
                    "approval-first-"
                )
            );

            assertThat(approval.get(10, TimeUnit.SECONDS).contentStatus())
                .isEqualTo(ContentStatus.WITHDRAWN);
            assertThat(confirmation.get(10, TimeUnit.SECONDS).errorCode())
                .isEqualTo(ErrorCode.RESERVATION_CONFIRM_CONFLICT);
        }

        assertWithdrawalInvalidatedAllActiveHolds(fixture, SESSION_CAPACITY);
        assertThat(reservationRepository.findAll())
            .noneMatch(reservation -> reservation.getCapacityHold().getHoldId()
                .equals(fixture.confirmingHoldId()));
    }

    @Test
    @Timeout(15)
    void 예약_확정이_먼저_커밋되면_승인이_소비_홀드와_확정_예약을_유지한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch reservationConfirmed = new CountDownLatch(1);
        CountDownLatch approvalStarted = new CountDownLatch(1);
        AtomicLong approvalConnectionId = new AtomicLong();

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<ReservationConfirmationResult> confirmation = executorService.submit(
                () -> transactionTemplate.execute(status -> {
                    ReservationConfirmationResult result = confirm(fixture, "confirmation-first-");
                    reservationConfirmed.countDown();
                    await(approvalStarted);
                    assertThat(awaitLockWait(approvalConnectionId.get())).isTrue();
                    return result;
                })
            );
            assertThat(reservationConfirmed.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ApproveContentWithdrawalResult> approval = executorService.submit(
                () -> approveInTrackedTransaction(fixture, approvalConnectionId, approvalStarted)
            );

            ReservationConfirmationResult confirmationResult = confirmation.get(10, TimeUnit.SECONDS);
            assertThat(confirmationResult.isSuccessful()).isTrue();
            assertThat(approval.get(10, TimeUnit.SECONDS).contentStatus())
                .isEqualTo(ContentStatus.WITHDRAWN);
            assertThat(capacityHoldRepository.findById(fixture.confirmingHoldId()))
                .hasValueSatisfying(hold -> assertThat(hold.getStatus())
                    .isEqualTo(CapacityHoldStatus.CONSUMED));
            assertThat(reservationRepository.findById(
                Long.valueOf(confirmationResult.response().reservationId())
            )).hasValueSatisfying(reservation -> assertThat(reservation.getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED));
        }

        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus())
                .isEqualTo(ContentStatus.WITHDRAWN));
        assertThat(contentSessionRepository.findById(fixture.sessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity())
                .isEqualTo(SESSION_CAPACITY - CONFIRMING_HOLD_QUANTITY));
        assertThat(capacityHoldRepository.findById(fixture.secondHoldId()))
            .hasValueSatisfying(this::assertInvalidated);
    }

    private ApproveContentWithdrawalResult approve(Fixture fixture) {
        return approvalUseCase.approve(
            fixture.adminId(),
            fixture.withdrawalRequestId(),
            UUID.randomUUID()
        );
    }

    private ApproveContentWithdrawalResult approveInTrackedTransaction(
        Fixture fixture,
        AtomicLong connectionId,
        CountDownLatch started
    ) {
        return transactionTemplate.execute(status -> {
            connectionId.set(findCurrentConnectionId());
            started.countDown();
            return approve(fixture);
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
        return holdCreationUseCase.create(
            fixture.holdCreatorId(),
            new CreateReservationHoldRequest(fixture.sessionId().toString(), 1)
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
        return confirmationUseCase.confirm(
            fixture.confirmingUserId(),
            fixture.confirmingHoldId().toString(),
            idempotencyKeyPrefix + System.nanoTime(),
            UUID.randomUUID()
        );
    }

    private void assertWithdrawalInvalidatedAllActiveHolds(Fixture fixture, int expectedCapacity) {
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus())
                .isEqualTo(ContentStatus.WITHDRAWN));
        assertThat(contentSessionRepository.findById(fixture.sessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity())
                .isEqualTo(expectedCapacity));
        assertThat(capacityHoldRepository.findById(fixture.confirmingHoldId()))
            .hasValueSatisfying(this::assertInvalidated);
        assertThat(capacityHoldRepository.findById(fixture.secondHoldId()))
            .hasValueSatisfying(this::assertInvalidated);
    }

    private void assertInvalidated(CapacityHold hold) {
        assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.INVALIDATED);
        assertThat(hold.getInvalidationReason()).isEqualTo("CONTENT_WITHDRAWN");
        assertThat(hold.getCapacityReleasedAt()).isNotNull();
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

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.saveAndFlush(new Region("WITHDRAW-" + suffix, "김해시", true));
            AppUser admin = saveUser("admin-" + suffix);
            roleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
                admin,
                UserRole.REGION_ADMIN,
                region
            ));
            AppUser operator = saveUser("operator-" + suffix);
            AppUser confirmingUser = saveUser("confirming-" + suffix);
            roleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
                confirmingUser,
                UserRole.VISITOR,
                null
            ));
            AppUser secondHoldUser = saveUser("second-hold-" + suffix);
            AppUser holdCreator = saveUser("hold-creator-" + suffix);
            Content content = contentRepository.saveAndFlush(new Content(
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
            contentLogRepository.saveAndFlush(new ContentLog(
                content,
                operator,
                ContentLogStatus.PUBLISHED,
                null,
                now.minusSeconds(86_400)
            ));
            ContentWithdrawalRequest request = withdrawalRequestRepository.saveAndFlush(
                ContentWithdrawalRequest.createPending(
                    content,
                    operator,
                    "a".repeat(64),
                    "운영 계획 변경",
                    now.minusSeconds(1_800)
                )
            );
            ContentSession session = new ContentSession(
                content,
                region,
                now.plusSeconds(3_600),
                now.plusSeconds(7_200),
                now.plusSeconds(1_800),
                now.plusSeconds(5_400),
                SESSION_CAPACITY
            );
            session.approve(admin, now.minusSeconds(600));
            session = contentSessionRepository.saveAndFlush(session);
            CapacityHold confirmingHold = createActiveHold(
                region,
                session,
                confirmingUser,
                CONFIRMING_HOLD_QUANTITY,
                now
            );
            CapacityHold secondHold = createActiveHold(
                region,
                session,
                secondHoldUser,
                SECOND_HOLD_QUANTITY,
                now
            );
            return new Fixture(
                admin.getUserId(),
                holdCreator.getUserId(),
                confirmingUser.getUserId(),
                content.getContentId(),
                request.getContentWithdrawalRequestId(),
                session.getSessionId(),
                confirmingHold.getHoldId(),
                secondHold.getHoldId()
            );
        });
    }

    private CapacityHold createActiveHold(
        Region region,
        ContentSession session,
        AppUser user,
        int quantity,
        Instant now
    ) {
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = remaining_capacity - ? WHERE session_id = ?",
            quantity,
            session.getSessionId()
        );
        return capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            user,
            quantity,
            CapacityHoldStatus.ACTIVE,
            now.plusSeconds(600),
            null,
            null,
            null,
            now
        ));
    }

    private AppUser saveUser(String prefix) {
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
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
        Long holdCreatorId,
        Long confirmingUserId,
        Long contentId,
        Long withdrawalRequestId,
        Long sessionId,
        Long confirmingHoldId,
        Long secondHoldId
    ) {
    }

    private record HoldCreationAttempt(CreateReservationHoldResponse response, ErrorCode errorCode) {
    }
}
