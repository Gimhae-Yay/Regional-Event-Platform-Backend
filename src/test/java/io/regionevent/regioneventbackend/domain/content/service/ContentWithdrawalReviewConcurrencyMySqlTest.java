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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentWithdrawalRequestRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
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
class ContentWithdrawalReviewConcurrencyMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final int LOCK_WAIT_CONFIRMATION_ATTEMPTS = 30;
    private static final long LOCK_WAIT_CONFIRMATION_INTERVAL_MILLIS = 100;

    private final ApproveContentWithdrawalUseCase approvalUseCase;
    private final RejectContentWithdrawalUseCase rejectionUseCase;
    private final ApproveContentRevisionUseCase revisionApprovalUseCase;
    private final RejectContentRevisionUseCase revisionRejectionUseCase;
    private final WithdrawContentRevisionUseCase revisionWithdrawalUseCase;
    private final SuspendContentUseCase suspensionUseCase;
    private final EndContentReservationsUseCase endingUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository roleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentLogRepository contentLogRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentWithdrawalRequestRepository withdrawalRequestRepository;
    private final ImageObjectRepository imageObjectRepository;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean
    private PublicCatalogCacheInvalidator cacheInvalidator;

    @Autowired
    ContentWithdrawalReviewConcurrencyMySqlTest(
        ApproveContentWithdrawalUseCase approvalUseCase,
        RejectContentWithdrawalUseCase rejectionUseCase,
        ApproveContentRevisionUseCase revisionApprovalUseCase,
        RejectContentRevisionUseCase revisionRejectionUseCase,
        WithdrawContentRevisionUseCase revisionWithdrawalUseCase,
        SuspendContentUseCase suspensionUseCase,
        EndContentReservationsUseCase endingUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository roleAssignmentRepository,
        ContentRepository contentRepository,
        ContentLogRepository contentLogRepository,
        ContentRevisionRepository contentRevisionRepository,
        ContentSessionRepository contentSessionRepository,
        ContentWithdrawalRequestRepository withdrawalRequestRepository,
        ImageObjectRepository imageObjectRepository,
        AuditEventRepository auditEventRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.approvalUseCase = approvalUseCase;
        this.rejectionUseCase = rejectionUseCase;
        this.revisionApprovalUseCase = revisionApprovalUseCase;
        this.revisionRejectionUseCase = revisionRejectionUseCase;
        this.revisionWithdrawalUseCase = revisionWithdrawalUseCase;
        this.suspensionUseCase = suspensionUseCase;
        this.endingUseCase = endingUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentLogRepository = contentLogRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.imageObjectRepository = imageObjectRepository;
        this.auditEventRepository = auditEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.grantLockMonitoringPrivileges();
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(15)
    void 동시_승인은_동일한_승인_결과로_수렴하고_터미널_전이를_한번만_기록한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<ApprovalAttempt> attempts;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<ApprovalAttempt> first = executorService.submit(
                () -> approveAfterStart(fixture, ready, start)
            );
            Future<ApprovalAttempt> second = executorService.submit(
                () -> approveAfterStart(fixture, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );
        }

        assertThat(attempts).extracting(ApprovalAttempt::connectionId).doesNotHaveDuplicates();
        assertThat(attempts).allMatch(ApprovalAttempt::isSuccessful);
        assertThat(attempts)
            .extracting(attempt -> attempt.result().approvedAt())
            .containsOnly(attempts.getFirst().result().approvedAt());
        assertApprovedTerminalState(fixture);
        assertThat(countSuccessfulAudits(
            AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST,
            fixture.withdrawalRequestId()
        )).isOne();
        assertThat(countSuccessfulAudits(AuditEventTargetType.CONTENT, fixture.contentId()))
            .isEqualTo(2);
    }

    @Test
    @Timeout(15)
    void 같은_사유의_동시_반려는_같은_결과로_수렴하고_감사를_한번만_기록한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<RejectionAttempt> attempts;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<RejectionAttempt> first = executorService.submit(
                () -> rejectAfterStart(fixture, "근거 보완 필요", ready, start)
            );
            Future<RejectionAttempt> second = executorService.submit(
                () -> rejectAfterStart(fixture, "근거 보완 필요", ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );
        }

        assertThat(attempts).extracting(RejectionAttempt::connectionId).doesNotHaveDuplicates();
        assertThat(attempts).allMatch(RejectionAttempt::isSuccessful);
        assertThat(attempts)
            .extracting(attempt -> attempt.result().rejectedAt())
            .containsOnly(attempts.getFirst().result().rejectedAt());
        assertRejectedTerminalState(fixture);
        assertThat(countSuccessfulAudits(
            AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST,
            fixture.withdrawalRequestId()
        )).isOne();
    }

    @Test
    @Timeout(15)
    void 다른_사유의_동시_반려는_최초_반려_하나만_유지한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<RejectionAttempt> attempts;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<RejectionAttempt> first = executorService.submit(
                () -> rejectAfterStart(fixture, "첫 반려 사유", ready, start)
            );
            Future<RejectionAttempt> second = executorService.submit(
                () -> rejectAfterStart(fixture, "두 번째 반려 사유", ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );
        }

        assertThat(attempts).extracting(RejectionAttempt::connectionId).doesNotHaveDuplicates();
        assertThat(attempts).filteredOn(RejectionAttempt::isSuccessful).singleElement();
        assertThat(attempts)
            .filteredOn(attempt -> !attempt.isSuccessful())
            .extracting(RejectionAttempt::errorCode)
            .containsExactly(ErrorCode.CONTENT_STATE_CONFLICT);
        assertRejectedTerminalState(fixture);
    }

    @Test
    @Timeout(15)
    void 승인이_반려보다_먼저_커밋되면_반려는_상태_충돌이다() throws Exception {
        Fixture fixture = createFixture();

        RejectionAttempt rejection = runApprovalBeforeRejection(fixture);

        assertThat(rejection.errorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT);
        assertApprovedTerminalState(fixture);
    }

    @Test
    @Timeout(15)
    void 반려가_승인보다_먼저_커밋되면_승인은_상태_충돌이다() throws Exception {
        Fixture fixture = createFixture();

        ApprovalAttempt approval = runRejectionBeforeApproval(fixture);

        assertThat(approval.errorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT);
        assertRejectedTerminalState(fixture);
    }

    @ParameterizedTest(name = "{0} 선행 뒤 전체 철회 승인")
    @EnumSource(CompetingCommand.class)
    @Timeout(15)
    void 수정본과_수명주기_명령이_먼저_커밋되면_계약된_상태로_수렴한다(
        CompetingCommand command
    ) throws Exception {
        Fixture fixture = createFixture();

        ApprovalAttempt approval = runCommandBeforeApproval(fixture, command);

        assertThat(contentRevisionRepository.findById(fixture.revisionId()))
            .hasValueSatisfying(revision -> assertThat(revision.getStatus())
                .isEqualTo(command.expectedRevisionStatus()));
        if (command.isRevisionCommand()) {
            assertThat(approval.isSuccessful()).isTrue();
            assertApprovedTerminalState(fixture);
        } else {
            assertThat(approval.errorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT);
            assertLifecycleTerminalState(fixture, command.expectedContentStatus());
        }
    }

    @ParameterizedTest(name = "전체 철회 승인 뒤 {0}")
    @EnumSource(CompetingCommand.class)
    @Timeout(15)
    void 전체_철회_승인이_먼저_커밋되면_후속_명령은_승인_상태를_바꾸지_않는다(
        CompetingCommand command
    ) throws Exception {
        Fixture fixture = createFixture();

        CommandAttempt commandAttempt = runApprovalBeforeCommand(fixture, command);

        assertApprovedTerminalState(fixture);
        assertThat(contentRevisionRepository.findById(fixture.revisionId()))
            .hasValueSatisfying(revision -> assertThat(revision.getStatus())
                .isEqualTo(ContentRevisionStatus.EDIT_INVALIDATED));
        if (command == CompetingCommand.AUTO_END) {
            assertThat(commandAttempt.skipped()).isTrue();
        } else {
            assertThat(commandAttempt.errorCode()).isEqualTo(command.errorAfterApproval());
        }
    }

    private RejectionAttempt runApprovalBeforeRejection(Fixture fixture) throws Exception {
        CountDownLatch approvalChanged = new CountDownLatch(1);
        CountDownLatch rejectionStarted = new CountDownLatch(1);
        AtomicLong rejectionConnectionId = new AtomicLong();
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<ApproveContentWithdrawalResult> approval = executorService.submit(
                () -> transactionTemplate.execute(status -> {
                    ApproveContentWithdrawalResult result = approve(fixture);
                    approvalChanged.countDown();
                    await(rejectionStarted);
                    assertThat(awaitLockWait(rejectionConnectionId.get())).isTrue();
                    return result;
                })
            );
            assertThat(approvalChanged.await(5, TimeUnit.SECONDS)).isTrue();
            Future<RejectionAttempt> rejection = executorService.submit(
                () -> rejectInTrackedTransaction(fixture, rejectionConnectionId, rejectionStarted)
            );
            assertThat(approval.get(10, TimeUnit.SECONDS).contentStatus())
                .isEqualTo(ContentStatus.WITHDRAWN);
            return rejection.get(10, TimeUnit.SECONDS);
        }
    }

    private ApprovalAttempt runRejectionBeforeApproval(Fixture fixture) throws Exception {
        CountDownLatch rejected = new CountDownLatch(1);
        CountDownLatch approvalStarted = new CountDownLatch(1);
        AtomicLong approvalConnectionId = new AtomicLong();
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<RejectContentWithdrawalResult> rejection = executorService.submit(
                () -> transactionTemplate.execute(status -> {
                    RejectContentWithdrawalResult result = reject(fixture, "근거 보완 필요");
                    rejected.countDown();
                    await(approvalStarted);
                    assertThat(awaitLockWait(approvalConnectionId.get())).isTrue();
                    return result;
                })
            );
            assertThat(rejected.await(5, TimeUnit.SECONDS)).isTrue();
            Future<ApprovalAttempt> approval = executorService.submit(
                () -> approveInTrackedTransaction(fixture, approvalConnectionId, approvalStarted)
            );
            assertThat(rejection.get(10, TimeUnit.SECONDS).status())
                .isEqualTo(ContentWithdrawalRequestStatus.REJECTED);
            return approval.get(10, TimeUnit.SECONDS);
        }
    }

    private ApprovalAttempt runCommandBeforeApproval(
        Fixture fixture,
        CompetingCommand command
    ) throws Exception {
        CountDownLatch commandCompleted = new CountDownLatch(1);
        CountDownLatch approvalStarted = new CountDownLatch(1);
        AtomicLong approvalConnectionId = new AtomicLong();
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<CommandAttempt> commandFuture = executorService.submit(
                () -> transactionTemplate.execute(status -> {
                    CommandAttempt result = executeCommand(fixture, command);
                    commandCompleted.countDown();
                    await(approvalStarted);
                    assertThat(awaitLockWait(approvalConnectionId.get())).isTrue();
                    return result;
                })
            );
            assertThat(commandCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<ApprovalAttempt> approvalFuture = executorService.submit(
                () -> approveInTrackedTransaction(fixture, approvalConnectionId, approvalStarted)
            );
            assertThat(commandFuture.get(10, TimeUnit.SECONDS).isSuccessful()).isTrue();
            return approvalFuture.get(10, TimeUnit.SECONDS);
        }
    }

    private CommandAttempt runApprovalBeforeCommand(
        Fixture fixture,
        CompetingCommand command
    ) throws Exception {
        CountDownLatch approvalChanged = new CountDownLatch(1);
        CountDownLatch commandStarted = new CountDownLatch(1);
        AtomicLong commandConnectionId = new AtomicLong();
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<ApproveContentWithdrawalResult> approvalFuture = executorService.submit(
                () -> transactionTemplate.execute(status -> {
                    ApproveContentWithdrawalResult result = approve(fixture);
                    approvalChanged.countDown();
                    await(commandStarted);
                    assertThat(awaitLockWait(commandConnectionId.get())).isTrue();
                    return result;
                })
            );
            assertThat(approvalChanged.await(5, TimeUnit.SECONDS)).isTrue();
            Future<CommandAttempt> commandFuture = executorService.submit(
                () -> commandInTrackedTransaction(
                    fixture,
                    command,
                    commandConnectionId,
                    commandStarted
                )
            );
            assertThat(approvalFuture.get(10, TimeUnit.SECONDS).contentStatus())
                .isEqualTo(ContentStatus.WITHDRAWN);
            return commandFuture.get(10, TimeUnit.SECONDS);
        }
    }

    private ApprovalAttempt approveAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        AtomicLong connectionId = new AtomicLong();
        try {
            ApproveContentWithdrawalResult result = transactionTemplate.execute(status -> {
                connectionId.set(findCurrentConnectionId());
                ready.countDown();
                await(start);
                return approve(fixture);
            });
            return new ApprovalAttempt(result, null, connectionId.get());
        } catch (BusinessException exception) {
            return new ApprovalAttempt(null, exception.getErrorCode(), connectionId.get());
        }
    }

    private RejectionAttempt rejectAfterStart(
        Fixture fixture,
        String reason,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        AtomicLong connectionId = new AtomicLong();
        try {
            RejectContentWithdrawalResult result = transactionTemplate.execute(status -> {
                connectionId.set(findCurrentConnectionId());
                ready.countDown();
                await(start);
                return reject(fixture, reason);
            });
            return new RejectionAttempt(result, null, connectionId.get());
        } catch (BusinessException exception) {
            return new RejectionAttempt(null, exception.getErrorCode(), connectionId.get());
        }
    }

    private ApprovalAttempt approveInTrackedTransaction(
        Fixture fixture,
        AtomicLong connectionId,
        CountDownLatch started
    ) {
        try {
            ApproveContentWithdrawalResult result = transactionTemplate.execute(status -> {
                connectionId.set(findCurrentConnectionId());
                started.countDown();
                return approve(fixture);
            });
            return new ApprovalAttempt(result, null, connectionId.get());
        } catch (BusinessException exception) {
            return new ApprovalAttempt(null, exception.getErrorCode(), connectionId.get());
        }
    }

    private RejectionAttempt rejectInTrackedTransaction(
        Fixture fixture,
        AtomicLong connectionId,
        CountDownLatch started
    ) {
        try {
            RejectContentWithdrawalResult result = transactionTemplate.execute(status -> {
                connectionId.set(findCurrentConnectionId());
                started.countDown();
                return reject(fixture, "근거 보완 필요");
            });
            return new RejectionAttempt(result, null, connectionId.get());
        } catch (BusinessException exception) {
            return new RejectionAttempt(null, exception.getErrorCode(), connectionId.get());
        }
    }

    private CommandAttempt commandInTrackedTransaction(
        Fixture fixture,
        CompetingCommand command,
        AtomicLong connectionId,
        CountDownLatch started
    ) {
        try {
            return transactionTemplate.execute(status -> {
                connectionId.set(findCurrentConnectionId());
                started.countDown();
                return executeCommand(fixture, command);
            });
        } catch (BusinessException exception) {
            return new CommandAttempt(false, exception.getErrorCode(), false);
        }
    }

    private CommandAttempt executeCommand(Fixture fixture, CompetingCommand command) {
        return switch (command) {
            case REVISION_APPROVAL -> {
                revisionApprovalUseCase.approve(
                    fixture.adminId(),
                    fixture.revisionId(),
                    UUID.randomUUID()
                );
                yield CommandAttempt.success();
            }
            case REVISION_REJECTION -> {
                revisionRejectionUseCase.reject(
                    fixture.adminId(),
                    fixture.revisionId(),
                    "수정 근거 부족",
                    UUID.randomUUID()
                );
                yield CommandAttempt.success();
            }
            case REVISION_WITHDRAWAL -> {
                revisionWithdrawalUseCase.withdraw(
                    fixture.operatorId(),
                    fixture.revisionId(),
                    "수정 요청 철회",
                    UUID.randomUUID()
                );
                yield CommandAttempt.success();
            }
            case SUSPENSION -> {
                suspensionUseCase.suspend(
                    fixture.adminId(),
                    fixture.contentId(),
                    "기상 악화",
                    UUID.randomUUID()
                );
                yield CommandAttempt.success();
            }
            case MANUAL_END -> {
                endingUseCase.end(
                    fixture.adminId(),
                    fixture.contentId(),
                    UUID.randomUUID()
                );
                yield CommandAttempt.success();
            }
            case AUTO_END -> {
                EndContentReservationsSystemResult result = endingUseCase.endBySystem(
                    fixture.contentId(),
                    UUID.randomUUID()
                );
                yield result.status() == EndContentReservationsSystemResult.Status.ENDED
                    ? CommandAttempt.success()
                    : CommandAttempt.skippedResult();
            }
        };
    }

    private ApproveContentWithdrawalResult approve(Fixture fixture) {
        return approvalUseCase.approve(
            fixture.adminId(),
            fixture.withdrawalRequestId(),
            UUID.randomUUID()
        );
    }

    private RejectContentWithdrawalResult reject(Fixture fixture, String reason) {
        return rejectionUseCase.reject(
            fixture.adminId(),
            fixture.withdrawalRequestId(),
            reason,
            UUID.randomUUID()
        );
    }

    private void assertApprovedTerminalState(Fixture fixture) {
        assertThat(withdrawalRequestRepository.findById(fixture.withdrawalRequestId()))
            .hasValueSatisfying(request -> assertThat(request.getStatus())
                .isEqualTo(ContentWithdrawalRequestStatus.APPROVED));
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus())
                .isEqualTo(ContentStatus.WITHDRAWN));
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED, ContentLogStatus.WITHDRAWN);
    }

    private void assertRejectedTerminalState(Fixture fixture) {
        assertThat(withdrawalRequestRepository.findById(fixture.withdrawalRequestId()))
            .hasValueSatisfying(request -> assertThat(request.getStatus())
                .isEqualTo(ContentWithdrawalRequestStatus.REJECTED));
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus())
                .isEqualTo(ContentStatus.PUBLISHED));
        assertThat(contentRevisionRepository.findById(fixture.revisionId()))
            .hasValueSatisfying(revision -> assertThat(revision.getStatus())
                .isEqualTo(ContentRevisionStatus.EDIT_REQUESTED));
    }

    private void assertLifecycleTerminalState(Fixture fixture, ContentStatus status) {
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(status));
        assertThat(withdrawalRequestRepository.findById(fixture.withdrawalRequestId()))
            .hasValueSatisfying(request -> assertThat(request.getStatus())
                .isEqualTo(ContentWithdrawalRequestStatus.INVALIDATED));
        assertThat(contentRevisionRepository.findById(fixture.revisionId()))
            .hasValueSatisfying(revision -> assertThat(revision.getStatus())
                .isEqualTo(ContentRevisionStatus.EDIT_INVALIDATED));
    }

    private long countSuccessfulAudits(AuditEventTargetType targetType, Long targetId) {
        return auditEventRepository.findAll().stream()
            .filter(event -> event.getTargetType() == targetType)
            .filter(event -> event.getTargetId().equals(targetId))
            .filter(event -> event.getResult() == AuditEventResult.SUCCESS)
            .count();
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
            Region region = regionRepository.saveAndFlush(new Region("REVIEW-" + suffix, "김해시", true));
            AppUser admin = saveUser("admin-" + suffix);
            roleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
                admin,
                UserRole.REGION_ADMIN,
                region
            ));
            AppUser operator = saveUser("operator-" + suffix);
            roleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
                operator,
                UserRole.OPERATOR,
                region
            ));
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
            ImageObject candidateImage = ImageObject.createUploadCandidate(
                "content/revision-" + suffix + ".webp",
                operator,
                region,
                "image/webp",
                1L,
                "revision-checksum-" + suffix,
                now.plusSeconds(3_600)
            );
            candidateImage.markLinked(now.minusSeconds(3_600));
            candidateImage = imageObjectRepository.saveAndFlush(candidateImage);
            ContentRevision revision = new ContentRevision(
                content,
                1,
                content.getVersionNo(),
                operator,
                ContentRevisionStatus.EDIT_REQUESTED,
                "수정 제목",
                "수정 설명",
                "김해문화의전당",
                "매일 11:00~19:00",
                "055-123-4567",
                "안전요원의 안내를 따라주세요.",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소할 수 있습니다.",
                0,
                null,
                now.minusSeconds(3_600),
                null,
                null,
                null,
                null,
                null,
                null
            );
            revision.assignCandidateImage(candidateImage, now.minusSeconds(3_600));
            revision = contentRevisionRepository.saveAndFlush(revision);
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
                now.minusSeconds(7_200),
                now.minusSeconds(3_600),
                now.minusSeconds(9_000),
                now.minusSeconds(5_400),
                10
            );
            session.approve(admin, now.minusSeconds(10_000));
            session.complete(now.minusSeconds(3_500));
            contentSessionRepository.saveAndFlush(session);
            return new Fixture(
                admin.getUserId(),
                operator.getUserId(),
                content.getContentId(),
                revision.getContentRevisionId(),
                request.getContentWithdrawalRequestId()
            );
        });
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

    private enum CompetingCommand {
        REVISION_APPROVAL(
            ContentRevisionStatus.EDIT_APPROVED,
            ContentStatus.PUBLISHED,
            ErrorCode.CONTENT_STATE_CONFLICT
        ),
        REVISION_REJECTION(
            ContentRevisionStatus.EDIT_REJECTED,
            ContentStatus.PUBLISHED,
            ErrorCode.CONTENT_STATE_CONFLICT
        ),
        REVISION_WITHDRAWAL(
            ContentRevisionStatus.EDIT_WITHDRAWN,
            ContentStatus.PUBLISHED,
            ErrorCode.CONTENT_STATE_CONFLICT
        ),
        SUSPENSION(
            ContentRevisionStatus.EDIT_INVALIDATED,
            ContentStatus.SUSPENDED,
            ErrorCode.CONTENT_SUSPEND_CONFLICT
        ),
        MANUAL_END(
            ContentRevisionStatus.EDIT_INVALIDATED,
            ContentStatus.ENDED,
            ErrorCode.CONTENT_END_CONFLICT
        ),
        AUTO_END(
            ContentRevisionStatus.EDIT_INVALIDATED,
            ContentStatus.ENDED,
            null
        );

        private final ContentRevisionStatus expectedRevisionStatus;
        private final ContentStatus expectedContentStatus;
        private final ErrorCode errorAfterApproval;

        CompetingCommand(
            ContentRevisionStatus expectedRevisionStatus,
            ContentStatus expectedContentStatus,
            ErrorCode errorAfterApproval
        ) {
            this.expectedRevisionStatus = expectedRevisionStatus;
            this.expectedContentStatus = expectedContentStatus;
            this.errorAfterApproval = errorAfterApproval;
        }

        private boolean isRevisionCommand() {
            return this == REVISION_APPROVAL
                || this == REVISION_REJECTION
                || this == REVISION_WITHDRAWAL;
        }

        private ContentRevisionStatus expectedRevisionStatus() {
            return expectedRevisionStatus;
        }

        private ContentStatus expectedContentStatus() {
            return expectedContentStatus;
        }

        private ErrorCode errorAfterApproval() {
            return errorAfterApproval;
        }
    }

    private record Fixture(
        Long adminId,
        Long operatorId,
        Long contentId,
        Long revisionId,
        Long withdrawalRequestId
    ) {
    }

    private record ApprovalAttempt(
        ApproveContentWithdrawalResult result,
        ErrorCode errorCode,
        long connectionId
    ) {

        private boolean isSuccessful() {
            return result != null;
        }
    }

    private record RejectionAttempt(
        RejectContentWithdrawalResult result,
        ErrorCode errorCode,
        long connectionId
    ) {

        private boolean isSuccessful() {
            return result != null;
        }
    }

    private record CommandAttempt(boolean isSuccessful, ErrorCode errorCode, boolean skipped) {

        private static CommandAttempt success() {
            return new CommandAttempt(true, null, false);
        }

        private static CommandAttempt skippedResult() {
            return new CommandAttempt(false, null, true);
        }
    }
}
