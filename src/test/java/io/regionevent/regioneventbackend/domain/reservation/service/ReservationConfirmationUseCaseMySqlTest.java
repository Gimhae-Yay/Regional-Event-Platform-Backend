package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.content.service.CancelContentSessionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;
import io.regionevent.regioneventbackend.domain.idempotency.repository.IdempotencyRecordRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationHoldRequest;
import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationHoldResponse;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationPriceSnapshotRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ReservationConfirmationUseCaseMySqlTest.ReservationLockOrderConfig.class)
class ReservationConfirmationUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private final ReservationConfirmationUseCase reservationConfirmationUseCase;
    private final MockMvc mockMvc;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final CreateReservationHoldUseCase createReservationHoldUseCase;
    private final CancelContentSessionUseCase cancelContentSessionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationPriceSnapshotRepository reservationPriceSnapshotRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AuditEventRepository auditEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final ReservationLockOrderTracker reservationLockOrderTracker;

    @Autowired
    ReservationConfirmationUseCaseMySqlTest(
        ReservationConfirmationUseCase reservationConfirmationUseCase,
        MockMvc mockMvc,
        JwtAccessTokenService jwtAccessTokenService,
        CreateReservationHoldUseCase createReservationHoldUseCase,
        CancelContentSessionUseCase cancelContentSessionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationPriceSnapshotRepository reservationPriceSnapshotRepository,
        ReservationRepository reservationRepository,
        IdempotencyRecordRepository idempotencyRecordRepository,
        AuditEventRepository auditEventRepository,
        ReservationLockOrderTracker reservationLockOrderTracker,
        PlatformTransactionManager transactionManager
    ) {
        this.reservationConfirmationUseCase = reservationConfirmationUseCase;
        this.mockMvc = mockMvc;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.createReservationHoldUseCase = createReservationHoldUseCase;
        this.cancelContentSessionUseCase = cancelContentSessionUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationPriceSnapshotRepository = reservationPriceSnapshotRepository;
        this.reservationRepository = reservationRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.auditEventRepository = auditEventRepository;
        this.reservationLockOrderTracker = reservationLockOrderTracker;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(
            registry,
            ReservationConfirmationUseCaseMySqlTest::withUseAffectedRows
        );
        registry.add("idempotency.lock-wait-timeout-seconds", () -> "3");
    }

    @Test
    @Timeout(10)
    void 동일_멱등_키_동시_확정은_예약을_한번만_생성한다() throws Exception {
        Fixture fixture = createFixture();
        String idempotencyKey = "same-key-" + System.nanoTime();

        List<ReservationConfirmationResult> results = confirmConcurrently(
            fixture,
            idempotencyKey,
            idempotencyKey
        );

        assertThat(results).allSatisfy(result -> assertThat(result.isSuccessful()).isTrue());
        assertThat(results)
            .extracting(result -> result.response().reservationId())
            .containsOnly(results.getFirst().response().reservationId());
        assertThat(reservationRepository.findAll())
            .filteredOn(reservation -> reservation.getCapacityHold().getHoldId().equals(fixture.capacityHold().getHoldId()))
            .hasSize(1);
        assertThat(idempotencyRecordRepository.findAll())
            .filteredOn(record -> record.getActor().getUserId().equals(fixture.user().getUserId()))
            .singleElement()
            .satisfies(record -> assertThat(record.getStatus()).isEqualTo(IdempotencyRecordStatus.SUCCEEDED));
        assertSuccessfulAuditEvents(fixture.capacityHold(), results.getFirst().response().reservationId());
    }

    @Test
    void 동일_멱등_키로_다른_홀드를_확정하면_멱등_키_충돌이_반환된다() {
        Fixture fixture = createFixture();
        CapacityHold otherCapacityHold = createActiveHold(fixture);
        String idempotencyKey = "conflict-key-" + System.nanoTime();

        ReservationConfirmationResult firstResult = confirm(
            fixture,
            fixture.capacityHold().getHoldId(),
            idempotencyKey
        );
        ReservationConfirmationResult conflictResult = confirm(
            fixture,
            otherCapacityHold.getHoldId(),
            idempotencyKey
        );

        assertThat(firstResult.isSuccessful()).isTrue();
        assertThat(conflictResult.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        assertThat(capacityHoldRepository.findById(otherCapacityHold.getHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE));
        assertThat(reservationRepository.findAll())
            .filteredOn(reservation -> reservation.getCapacityHold().getHoldId()
                .equals(fixture.capacityHold().getHoldId()))
            .hasSize(1);
        assertThat(reservationRepository.findAll())
            .noneMatch(reservation -> reservation.getCapacityHold().getHoldId()
                .equals(otherCapacityHold.getHoldId()));
        assertSuccessfulAuditEvents(fixture.capacityHold(), firstResult.response().reservationId());
    }

    @Test
    @Timeout(10)
    void 서로_다른_멱등_키로_동일_홀드를_동시_확정하면_하나만_성공한다() throws Exception {
        Fixture fixture = createFixture();

        List<ReservationConfirmationResult> results = confirmConcurrently(
            fixture,
            "first-key-" + System.nanoTime(),
            "second-key-" + System.nanoTime()
        );

        assertThat(results).filteredOn(ReservationConfirmationResult::isSuccessful).hasSize(1);
        assertThat(results)
            .filteredOn(result -> !result.isSuccessful())
            .singleElement()
            .satisfies(result -> assertThat(result.errorCode()).isEqualTo(ErrorCode.RESERVATION_CONFIRM_CONFLICT));
        assertThat(capacityHoldRepository.findById(fixture.capacityHold().getHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED));
        assertThat(reservationRepository.findAll())
            .filteredOn(reservation -> reservation.getCapacityHold().getHoldId().equals(fixture.capacityHold().getHoldId()))
            .hasSize(1);
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getResult() == AuditEventResult.FAILURE)
            .anySatisfy(event -> assertThat(event.getReasonCode()).isEqualTo("RESERVATION_CONFIRM_CONFLICT"));
        ReservationConfirmationResult successfulResult = results.stream()
            .filter(ReservationConfirmationResult::isSuccessful)
            .findFirst()
            .orElseThrow();
        assertSuccessfulAuditEvents(fixture.capacityHold(), successfulResult.response().reservationId());
    }

    @Test
    void 이미_예약이_연결된_활성_홀드는_실패_멱등_결과로_409를_반환한다() throws Exception {
        Fixture fixture = createFixture();
        createExistingReservation(fixture);
        String idempotencyKey = "already-reserved-hold-" + System.nanoTime();

        performConfirmRequest(fixture, idempotencyKey)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_CONFIRM_CONFLICT"));
        performConfirmRequest(fixture, idempotencyKey)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_CONFIRM_CONFLICT"));

        assertThat(capacityHoldRepository.findById(fixture.capacityHold().getHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE));
        assertThat(reservationRepository.findAll())
            .filteredOn(reservation -> reservation.getCapacityHold().getHoldId().equals(fixture.capacityHold().getHoldId()))
            .hasSize(1);
        assertThat(idempotencyRecordRepository.findAll())
            .filteredOn(record -> record.getActor().getUserId().equals(fixture.user().getUserId()))
            .singleElement()
            .satisfies(record -> {
                assertThat(record.getStatus()).isEqualTo(IdempotencyRecordStatus.FAILED);
                assertThat(record.getResultCode()).isEqualTo("RESERVATION_CONFIRM_CONFLICT");
            });
        assertThat(auditEventRepository.findAll())
            .noneMatch(event -> event.getResult() == AuditEventResult.SUCCESS);
    }

    @Test
    @Timeout(10)
    void cancelSessionAndConfirmReservationConcurrently_terminalizesMutableReservationState() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        reservationLockOrderTracker.prepare(
            fixture.contentSession().getContent().getContentId(),
            fixture.contentSession().getSessionId()
        );

        try {
            try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
                Future<?> cancel = executorService.submit(
                    () -> reservationLockOrderTracker.runAsSessionCancel(
                        () -> cancelAfterStart(fixture, ready, start)
                    )
                );
                Future<ReservationConfirmationResult> confirmation = executorService.submit(
                    () -> confirmAfterStart(fixture, "cancel-race-key-" + System.nanoTime(), ready, start)
                );

                assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
                start.countDown();

                cancel.get(5, TimeUnit.SECONDS);
                ReservationConfirmationResult confirmationResult = confirmation.get(5, TimeUnit.SECONDS);
                if (!confirmationResult.isSuccessful()) {
                    assertThat(confirmationResult.errorCode()).isEqualTo(ErrorCode.RESERVATION_CONFIRM_CONFLICT);
                }
            }

            assertThat(reservationLockOrderTracker.sessionCancelLockOrder())
                .containsExactly(ReservationLockTarget.CONTENT, ReservationLockTarget.CONTENT_SESSION);
            assertSessionCancellationTerminalState(fixture);
        } finally {
            reservationLockOrderTracker.reset();
        }
    }

    @Test
    @Timeout(10)
    void 홀드생성과_예약확정이_동시에_실행되어도_잠금순서에따라_완료되고_정원이_일치한다() throws Exception {
        ConcurrentFixture fixture = createConcurrentFixture(2);
        reservationLockOrderTracker.prepare(
            fixture.contentSession().getContent().getContentId(),
            fixture.contentSession().getSessionId()
        );

        try {
            try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
                Future<CreateReservationHoldResponse> createFuture = executorService.submit(
                    () -> reservationLockOrderTracker.runAsHoldCreate(() -> createReservationHoldUseCase.create(
                        fixture.holdCreator().getUserId(),
                        new CreateReservationHoldRequest(fixture.contentSession().getSessionId().toString(), 1)
                    ))
                );
                assertThat(reservationLockOrderTracker.awaitHoldCreateContentLock()).isTrue();

                Future<ReservationConfirmationResult> confirmFuture = executorService.submit(
                    () -> reservationLockOrderTracker.runAsReservationConfirm(() -> reservationConfirmationUseCase.confirm(
                        fixture.holdOwner().getUserId(),
                        fixture.capacityHold().getHoldId().toString(),
                        "hold-confirm-race-" + System.nanoTime(),
                        UUID.randomUUID()
                    ))
                );
                assertThat(reservationLockOrderTracker.awaitReservationConfirmContentLockAttempt()).isTrue();

                reservationLockOrderTracker.releaseHoldCreateSessionLock();
                assertThat(createFuture.get(5, TimeUnit.SECONDS)).isNotNull();
                assertThat(confirmFuture.get(5, TimeUnit.SECONDS).isSuccessful()).isTrue();
            }

            assertThat(reservationLockOrderTracker.holdCreateLockOrder())
                .containsExactly(ReservationLockTarget.CONTENT, ReservationLockTarget.CONTENT_SESSION);
            assertThat(reservationLockOrderTracker.reservationConfirmLockOrder())
                .containsExactly(ReservationLockTarget.CONTENT, ReservationLockTarget.CONTENT_SESSION);
        } finally {
            reservationLockOrderTracker.releaseHoldCreateSessionLock();
            reservationLockOrderTracker.reset();
        }

        transactionTemplate.executeWithoutResult(status -> {
            ContentSession session = contentSessionRepository.findById(fixture.contentSession().getSessionId())
                .orElseThrow();
            assertThat(session.getRemainingCapacity()).isZero();
            assertThat(capacityHoldRepository.findAll())
                .filteredOn(hold -> hold.getContentSession().getSessionId().equals(session.getSessionId()))
                .extracting(CapacityHold::getStatus)
                .containsExactlyInAnyOrder(CapacityHoldStatus.ACTIVE, CapacityHoldStatus.CONSUMED);
            assertThat(reservationRepository.findAll())
                .filteredOn(reservation -> reservation.getContentSession().getSessionId().equals(session.getSessionId()))
                .extracting(reservation -> reservation.getStatus())
                .containsExactly(ReservationStatus.CONFIRMED);
        });
    }

    @Test
    @Timeout(10)
    void 홀드생성과_예약확정을_동시에_시작하면_시스템예외없이_정합성을_유지한다() throws Exception {
        ConcurrentFixture fixture = createConcurrentFixture(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<MixedReservationOperationResult> results;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<MixedReservationOperationResult> holdCreation = executorService.submit(
                () -> createHoldAfterStart(fixture, ready, start)
            );
            Future<MixedReservationOperationResult> reservationConfirmation = executorService.submit(
                () -> confirmHoldAfterStart(fixture, ready, start)
            );

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = List.of(
                holdCreation.get(5, TimeUnit.SECONDS),
                reservationConfirmation.get(5, TimeUnit.SECONDS)
            );
        }

        assertThat(results).allSatisfy(result -> assertThat(result.systemFailure()).isNull());
        assertThat(results)
            .filteredOn(MixedReservationOperationResult::isSuccessful)
            .singleElement()
            .satisfies(result -> assertThat(result.operation())
                .isEqualTo(MixedReservationOperation.RESERVATION_CONFIRMATION));
        assertThat(results)
            .filteredOn(result -> !result.isSuccessful())
            .singleElement()
            .satisfies(result -> {
                assertThat(result.operation()).isEqualTo(MixedReservationOperation.HOLD_CREATION);
                assertThat(result.errorCode()).isEqualTo(ErrorCode.RESERVATION_HOLD_CONFLICT);
            });

        transactionTemplate.executeWithoutResult(status -> {
            ContentSession session = contentSessionRepository.findById(fixture.contentSession().getSessionId())
                .orElseThrow();
            assertThat(session.getRemainingCapacity()).isZero();
            assertThat(capacityHoldRepository.findAll())
                .filteredOn(hold -> hold.getContentSession().getSessionId().equals(session.getSessionId()))
                .singleElement()
                .satisfies(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED));
            assertThat(reservationRepository.findAll())
                .filteredOn(reservation -> reservation.getContentSession().getSessionId().equals(session.getSessionId()))
                .extracting(reservation -> reservation.getStatus())
                .containsExactly(ReservationStatus.CONFIRMED);
        });
    }

    @Test
    void paidContent_isRejectedByP0FreeReservationConfirmation() {
        Fixture fixture = createFixture(20_000);

        ReservationConfirmationResult result = confirm(
            fixture,
            fixture.capacityHold().getHoldId(),
            "paid-content-" + System.nanoTime()
        );

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.RESERVATION_CONFIRM_CONFLICT);
        assertThat(capacityHoldRepository.findById(fixture.capacityHold().getHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE));
        assertThat(reservationRepository.findAll())
            .noneMatch(reservation -> reservation.getCapacityHold().getHoldId()
                .equals(fixture.capacityHold().getHoldId()));
    }

    @Test
    void 비공개_지역의_활성_홀드는_무료_예약으로_확정하지_않는다() {
        Fixture fixture = createFixture();
        changeRegionVisibility(fixture.capacityHold().getRegion().getRegionId(), false);

        ReservationConfirmationResult result = confirm(
            fixture,
            fixture.capacityHold().getHoldId(),
            "private-region-" + System.nanoTime()
        );

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.RESERVATION_CONFIRM_CONFLICT);
        assertThat(capacityHoldRepository.findById(fixture.capacityHold().getHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE));
        assertThat(reservationRepository.findAll())
            .noneMatch(reservation -> reservation.getCapacityHold().getHoldId()
                .equals(fixture.capacityHold().getHoldId()));
        assertThat(reservationPriceSnapshotRepository.findAll())
            .noneMatch(snapshot -> snapshot.getCapacityHold().getHoldId()
                .equals(fixture.capacityHold().getHoldId()));
    }

    @Test
    void p1PriceSnapshotLinkedHold_isRejectedByP0FreeReservationConfirmation() {
        Fixture fixture = createFixture();
        reservationPriceSnapshotRepository.saveAndFlush(new ReservationPriceSnapshot(
            fixture.capacityHold(),
            null,
            0,
            0,
            0,
            "KRW",
            Instant.now()
        ));

        ReservationConfirmationResult result = confirm(
            fixture,
            fixture.capacityHold().getHoldId(),
            "p1-snapshot-" + System.nanoTime()
        );

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.RESERVATION_CONFIRM_CONFLICT);
        assertThat(capacityHoldRepository.findById(fixture.capacityHold().getHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE));
        assertThat(reservationRepository.findAll())
            .noneMatch(reservation -> reservation.getCapacityHold().getHoldId()
                .equals(fixture.capacityHold().getHoldId()));
    }

    private List<ReservationConfirmationResult> confirmConcurrently(
        Fixture fixture,
        String firstIdempotencyKey,
        String secondIdempotencyKey
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<ReservationConfirmationResult> first = executorService.submit(
                () -> confirmAfterStart(fixture, firstIdempotencyKey, ready, start)
            );
            Future<ReservationConfirmationResult> second = executorService.submit(
                () -> confirmAfterStart(fixture, secondIdempotencyKey, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }
    }

    private ReservationConfirmationResult confirmAfterStart(
        Fixture fixture,
        String idempotencyKey,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return reservationConfirmationUseCase.confirm(
            fixture.user().getUserId(),
            fixture.capacityHold().getHoldId().toString(),
            idempotencyKey,
            UUID.randomUUID()
        );
    }

    private MixedReservationOperationResult createHoldAfterStart(
        ConcurrentFixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);

        try {
            createReservationHoldUseCase.create(
                fixture.holdCreator().getUserId(),
                new CreateReservationHoldRequest(fixture.contentSession().getSessionId().toString(), 1)
            );
            return MixedReservationOperationResult.success(MixedReservationOperation.HOLD_CREATION);
        } catch (BusinessException exception) {
            return MixedReservationOperationResult.businessFailure(
                MixedReservationOperation.HOLD_CREATION,
                exception.getErrorCode()
            );
        } catch (RuntimeException exception) {
            return MixedReservationOperationResult.systemFailure(
                MixedReservationOperation.HOLD_CREATION,
                exception
            );
        }
    }

    private MixedReservationOperationResult confirmHoldAfterStart(
        ConcurrentFixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);

        try {
            ReservationConfirmationResult result = reservationConfirmationUseCase.confirm(
                fixture.holdOwner().getUserId(),
                fixture.capacityHold().getHoldId().toString(),
                "mixed-hold-confirm-" + System.nanoTime(),
                UUID.randomUUID()
            );
            if (result.isSuccessful()) {
                return MixedReservationOperationResult.success(MixedReservationOperation.RESERVATION_CONFIRMATION);
            }
            return MixedReservationOperationResult.businessFailure(
                MixedReservationOperation.RESERVATION_CONFIRMATION,
                result.errorCode()
            );
        } catch (RuntimeException exception) {
            return MixedReservationOperationResult.systemFailure(
                MixedReservationOperation.RESERVATION_CONFIRMATION,
                exception
            );
        }
    }

    private Void cancelAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        cancelContentSessionUseCase.cancel(
            fixture.operator().getUserId(),
            fixture.contentSession().getSessionId(),
            "Session cancelled",
            UUID.randomUUID()
        );
        return null;
    }

    private ReservationConfirmationResult confirm(Fixture fixture, Long holdId, String idempotencyKey) {
        return reservationConfirmationUseCase.confirm(
            fixture.user().getUserId(),
            holdId.toString(),
            idempotencyKey,
            UUID.randomUUID()
        );
    }

    private void changeRegionVisibility(Long regionId, boolean isPublic) {
        transactionTemplate.executeWithoutResult(status -> regionRepository.findById(regionId)
            .orElseThrow()
            .changeVisibility(isPublic));
    }

    private org.springframework.test.web.servlet.ResultActions performConfirmRequest(
        Fixture fixture,
        String idempotencyKey
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/reservation-holds/{holdId}/confirm", fixture.capacityHold().getHoldId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(fixture.user().getUserId()))
            .header("Idempotency-Key", idempotencyKey));
    }

    private void createExistingReservation(Fixture fixture) {
        transactionTemplate.executeWithoutResult(status -> {
            CapacityHold capacityHold = capacityHoldRepository.findById(fixture.capacityHold().getHoldId())
                .orElseThrow();
            Instant confirmedAt = Instant.now();
            reservationRepository.saveAndFlush(new Reservation(
                "R" + Long.toUnsignedString(System.nanoTime()),
                UUID.randomUUID().toString(),
                capacityHold.getRegion(),
                capacityHold,
                capacityHold.getContentSession(),
                capacityHold.getUser(),
                ReservationStatus.CONFIRMED,
                confirmedAt,
                null,
                null,
                null,
                null
            ));
        });
    }

    private CapacityHold createActiveHold(Fixture fixture) {
        return transactionTemplate.execute(status -> {
            CapacityHold existingCapacityHold = capacityHoldRepository.findById(fixture.capacityHold().getHoldId())
                .orElseThrow();
            Instant now = Instant.now();
            return capacityHoldRepository.save(new CapacityHold(
                existingCapacityHold.getRegion(),
                existingCapacityHold.getContentSession(),
                existingCapacityHold.getUser(),
                1,
                CapacityHoldStatus.ACTIVE,
                now.plusSeconds(600),
                null,
                null,
                null,
                now
            ));
        });
    }

    private void assertSessionCancellationTerminalState(Fixture fixture) {
        transactionTemplate.executeWithoutResult(status -> {
            ContentSession session = contentSessionRepository.findById(fixture.contentSession().getSessionId())
                .orElseThrow();
            assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.CANCELLED);
            assertThat(session.getRemainingCapacity()).isEqualTo(1);
            assertThat(capacityHoldRepository.findAll())
                .filteredOn(hold -> hold.getContentSession().getSessionId().equals(session.getSessionId()))
                .noneMatch(hold -> hold.getStatus() == CapacityHoldStatus.ACTIVE);
            assertThat(reservationRepository.findAll())
                .filteredOn(reservation -> reservation.getContentSession().getSessionId().equals(session.getSessionId()))
                .noneMatch(reservation -> reservation.getStatus() == ReservationStatus.CONFIRMED);
        });
    }

    private void assertSuccessfulAuditEvents(CapacityHold capacityHold, String reservationId) {
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getResult() == AuditEventResult.SUCCESS)
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.CAPACITY_HOLD)
            .filteredOn(event -> event.getTargetId().equals(capacityHold.getHoldId()))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getPreviousState()).isEqualTo("ACTIVE");
                assertThat(event.getNextState()).isEqualTo("CONSUMED");
            });
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getResult() == AuditEventResult.SUCCESS)
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.RESERVATION)
            .filteredOn(event -> event.getTargetId().toString().equals(reservationId))
            .singleElement()
            .satisfies(event -> assertThat(event.getNextState()).isEqualTo("CONFIRMED"));
    }

    private Fixture createFixture() {
        return createFixture(0);
    }

    private Fixture createFixture(long reservationPrice) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.save(new Region("R" + suffix, "김해시", true));
            AppUser user = appUserRepository.save(new AppUser(
                "visitor-" + suffix + "@example.com",
                "hashed-password",
                "예약 사용자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(user, UserRole.VISITOR, null));
            AppUser operator = appUserRepository.save(new AppUser(
                "operator-" + suffix + "@example.com",
                "hashed-password",
                "운영자",
                "010-9876-5432",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
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
                "안내를 따라주세요.",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소할 수 있습니다.",
                reservationPrice,
                now
            ));
            ContentSession session = new ContentSession(
                content,
                region,
                now.plusSeconds(3_600),
                now.plusSeconds(10_800),
                now.plusSeconds(1_800),
                now.plusSeconds(9_000),
                1
            );
            session.approve(operator, now);
            ContentSession savedSession = contentSessionRepository.saveAndFlush(session);
            contentSessionRepository.decreaseRemainingCapacityIfReservable(
                savedSession.getSessionId(),
                1,
                ContentStatus.PUBLISHED,
                ContentSessionStatus.SCHEDULED
            );
            CapacityHold capacityHold = capacityHoldRepository.save(new CapacityHold(
                region,
                savedSession,
                user,
                1,
                CapacityHoldStatus.ACTIVE,
                now.plusSeconds(600),
                null,
                null,
                null,
                now
            ));
            return new Fixture(user, operator, savedSession, capacityHold);
        });
    }

    private ConcurrentFixture createConcurrentFixture(int capacity) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.save(new Region("R" + suffix, "김해시", true));
            AppUser holdOwner = appUserRepository.save(new AppUser(
                "owner-" + suffix + "@example.com",
                "hashed-password",
                "홀드 소유자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(holdOwner, UserRole.VISITOR, null));
            AppUser holdCreator = appUserRepository.save(new AppUser(
                "creator-" + suffix + "@example.com",
                "hashed-password",
                "홀드 생성자",
                "010-2345-6789",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(holdCreator, UserRole.VISITOR, null));
            AppUser operator = appUserRepository.save(new AppUser(
                "operator-" + suffix + "@example.com",
                "hashed-password",
                "운영자",
                "010-9876-5432",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
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
                "안내를 따라주세요.",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소할 수 있습니다.",
                now
            ));
            ContentSession contentSession = new ContentSession(
                content,
                region,
                now.plusSeconds(3_600),
                now.plusSeconds(10_800),
                now.plusSeconds(1_800),
                now.plusSeconds(9_000),
                capacity
            );
            contentSession.approve(operator, now);
            ContentSession savedSession = contentSessionRepository.saveAndFlush(contentSession);
            contentSessionRepository.decreaseRemainingCapacityIfReservable(
                savedSession.getSessionId(),
                1,
                ContentStatus.PUBLISHED,
                ContentSessionStatus.SCHEDULED
            );
            CapacityHold capacityHold = capacityHoldRepository.save(new CapacityHold(
                region,
                savedSession,
                holdOwner,
                1,
                CapacityHoldStatus.ACTIVE,
                now.plusSeconds(600),
                null,
                null,
                null,
                now
            ));
            return new ConcurrentFixture(holdOwner, holdCreator, savedSession, capacityHold);
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent test latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent test interrupted", exception);
        }
    }

    private static String withUseAffectedRows(String jdbcUrl) {
        String parameterPrefix = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + parameterPrefix + "useAffectedRows=true";
    }

    private record Fixture(AppUser user, AppUser operator, ContentSession contentSession, CapacityHold capacityHold) {
    }

    private record ConcurrentFixture(
        AppUser holdOwner,
        AppUser holdCreator,
        ContentSession contentSession,
        CapacityHold capacityHold
    ) {
    }

    private record MixedReservationOperationResult(
        MixedReservationOperation operation,
        ErrorCode errorCode,
        RuntimeException systemFailure
    ) {

        private static MixedReservationOperationResult success(MixedReservationOperation operation) {
            return new MixedReservationOperationResult(operation, null, null);
        }

        private static MixedReservationOperationResult businessFailure(
            MixedReservationOperation operation,
            ErrorCode errorCode
        ) {
            return new MixedReservationOperationResult(operation, errorCode, null);
        }

        private static MixedReservationOperationResult systemFailure(
            MixedReservationOperation operation,
            RuntimeException systemFailure
        ) {
            return new MixedReservationOperationResult(operation, null, systemFailure);
        }

        private boolean isSuccessful() {
            return errorCode == null && systemFailure == null;
        }
    }

    private enum MixedReservationOperation {
        HOLD_CREATION,
        RESERVATION_CONFIRMATION
    }

    @TestConfiguration
    static class ReservationLockOrderConfig {

        @Bean
        ReservationLockOrderTracker reservationLockOrderTracker() {
            return new ReservationLockOrderTracker();
        }

        @Bean
        @Primary
        LockTrackingContentService lockTrackingContentService(
            ContentRepository contentRepository,
            ReservationLockOrderTracker reservationLockOrderTracker
        ) {
            return new LockTrackingContentService(contentRepository, reservationLockOrderTracker);
        }

        @Bean
        @Primary
        LockTrackingContentSessionService lockTrackingContentSessionService(
            ContentSessionRepository contentSessionRepository,
            ReservationLockOrderTracker reservationLockOrderTracker
        ) {
            return new LockTrackingContentSessionService(contentSessionRepository, reservationLockOrderTracker);
        }
    }

    static class LockTrackingContentService extends ContentService {

        private final ReservationLockOrderTracker reservationLockOrderTracker;

        LockTrackingContentService(
            ContentRepository contentRepository,
            ReservationLockOrderTracker reservationLockOrderTracker
        ) {
            super(contentRepository);
            this.reservationLockOrderTracker = reservationLockOrderTracker;
        }

        @Override
        public boolean lockPublishedReservationTarget(Long contentId) {
            reservationLockOrderTracker.recordReservationConfirmContentLockAttempt(contentId);
            boolean locked = super.lockPublishedReservationTarget(contentId);
            reservationLockOrderTracker.recordContentLock(contentId);
            return locked;
        }

        @Override
        public boolean lockPublishedCapacityHoldTarget(Long contentId) {
            boolean locked = super.lockPublishedCapacityHoldTarget(contentId);
            reservationLockOrderTracker.recordContentLock(contentId);
            return locked;
        }

        @Override
        public Content findForUpdate(Long contentId) {
            Content content = super.findForUpdate(contentId);
            reservationLockOrderTracker.recordContentLock(contentId);
            return content;
        }
    }

    static class LockTrackingContentSessionService extends ContentSessionService {

        private final ReservationLockOrderTracker reservationLockOrderTracker;

        LockTrackingContentSessionService(
            ContentSessionRepository contentSessionRepository,
            ReservationLockOrderTracker reservationLockOrderTracker
        ) {
            super(contentSessionRepository);
            this.reservationLockOrderTracker = reservationLockOrderTracker;
        }

        @Override
        @Transactional(propagation = Propagation.MANDATORY)
        public ContentSession findForUpdate(Long sessionId) {
            ContentSession contentSession = super.findForUpdate(sessionId);
            reservationLockOrderTracker.recordHoldCreateSessionLock(sessionId);
            return contentSession;
        }

        @Override
        public boolean lockConfirmableReservationTarget(Long sessionId) {
            boolean locked = super.lockConfirmableReservationTarget(sessionId);
            reservationLockOrderTracker.recordReservationConfirmSessionLock(sessionId);
            return locked;
        }

        @Override
        public ContentSession findCancelTargetForUpdate(Long sessionId) {
            ContentSession contentSession = super.findCancelTargetForUpdate(sessionId);
            reservationLockOrderTracker.recordSessionCancelSessionLock(sessionId);
            return contentSession;
        }
    }

    static class ReservationLockOrderTracker {

        private final ThreadLocal<ReservationLockOperation> currentOperation = new ThreadLocal<>();
        private final List<ReservationLockTarget> holdCreateLockOrder = new CopyOnWriteArrayList<>();
        private final List<ReservationLockTarget> reservationConfirmLockOrder = new CopyOnWriteArrayList<>();
        private final List<ReservationLockTarget> sessionCancelLockOrder = new CopyOnWriteArrayList<>();
        private volatile Long targetContentId;
        private volatile Long targetSessionId;
        private volatile CountDownLatch holdCreateContentLocked = new CountDownLatch(1);
        private volatile CountDownLatch allowHoldCreateSessionLock = new CountDownLatch(1);
        private volatile CountDownLatch reservationConfirmContentLockAttempted = new CountDownLatch(1);

        void prepare(Long contentId, Long sessionId) {
            targetContentId = contentId;
            targetSessionId = sessionId;
            holdCreateLockOrder.clear();
            reservationConfirmLockOrder.clear();
            sessionCancelLockOrder.clear();
            holdCreateContentLocked = new CountDownLatch(1);
            allowHoldCreateSessionLock = new CountDownLatch(1);
            reservationConfirmContentLockAttempted = new CountDownLatch(1);
        }

        void reset() {
            targetContentId = null;
            targetSessionId = null;
            currentOperation.remove();
        }

        <T> T runAsHoldCreate(Supplier<T> action) {
            return runAs(ReservationLockOperation.HOLD_CREATE, action);
        }

        <T> T runAsReservationConfirm(Supplier<T> action) {
            return runAs(ReservationLockOperation.RESERVATION_CONFIRM, action);
        }

        <T> T runAsSessionCancel(Supplier<T> action) {
            return runAs(ReservationLockOperation.SESSION_CANCEL, action);
        }

        boolean awaitHoldCreateContentLock() throws InterruptedException {
            return holdCreateContentLocked.await(3, TimeUnit.SECONDS);
        }

        boolean awaitReservationConfirmContentLockAttempt() throws InterruptedException {
            return reservationConfirmContentLockAttempted.await(3, TimeUnit.SECONDS);
        }

        void releaseHoldCreateSessionLock() {
            allowHoldCreateSessionLock.countDown();
        }

        List<ReservationLockTarget> holdCreateLockOrder() {
            return List.copyOf(holdCreateLockOrder);
        }

        List<ReservationLockTarget> reservationConfirmLockOrder() {
            return List.copyOf(reservationConfirmLockOrder);
        }

        List<ReservationLockTarget> sessionCancelLockOrder() {
            return List.copyOf(sessionCancelLockOrder);
        }

        void recordReservationConfirmContentLockAttempt(Long contentId) {
            if (isReservationConfirmContent(contentId)) {
                reservationConfirmContentLockAttempted.countDown();
            }
        }

        void recordContentLock(Long contentId) {
            if (isHoldCreateContent(contentId)) {
                holdCreateLockOrder.add(ReservationLockTarget.CONTENT);
                holdCreateContentLocked.countDown();
                await(allowHoldCreateSessionLock);
                return;
            }
            if (isReservationConfirmContent(contentId)) {
                reservationConfirmLockOrder.add(ReservationLockTarget.CONTENT);
                return;
            }
            if (isSessionCancelContent(contentId)) {
                sessionCancelLockOrder.add(ReservationLockTarget.CONTENT);
            }
        }

        void recordHoldCreateSessionLock(Long sessionId) {
            if (isHoldCreateSession(sessionId)) {
                holdCreateLockOrder.add(ReservationLockTarget.CONTENT_SESSION);
            }
        }

        void recordReservationConfirmSessionLock(Long sessionId) {
            if (isReservationConfirmSession(sessionId)) {
                reservationConfirmLockOrder.add(ReservationLockTarget.CONTENT_SESSION);
            }
        }

        void recordSessionCancelSessionLock(Long sessionId) {
            if (isSessionCancelSession(sessionId)) {
                sessionCancelLockOrder.add(ReservationLockTarget.CONTENT_SESSION);
            }
        }

        private <T> T runAs(ReservationLockOperation operation, Supplier<T> action) {
            currentOperation.set(operation);
            try {
                return action.get();
            } finally {
                currentOperation.remove();
            }
        }

        private boolean isHoldCreateContent(Long contentId) {
            return currentOperation.get() == ReservationLockOperation.HOLD_CREATE
                && contentId.equals(targetContentId);
        }

        private boolean isHoldCreateSession(Long sessionId) {
            return currentOperation.get() == ReservationLockOperation.HOLD_CREATE
                && sessionId.equals(targetSessionId);
        }

        private boolean isReservationConfirmContent(Long contentId) {
            return currentOperation.get() == ReservationLockOperation.RESERVATION_CONFIRM
                && contentId.equals(targetContentId);
        }

        private boolean isReservationConfirmSession(Long sessionId) {
            return currentOperation.get() == ReservationLockOperation.RESERVATION_CONFIRM
                && sessionId.equals(targetSessionId);
        }

        private boolean isSessionCancelContent(Long contentId) {
            return currentOperation.get() == ReservationLockOperation.SESSION_CANCEL
                && contentId.equals(targetContentId);
        }

        private boolean isSessionCancelSession(Long sessionId) {
            return currentOperation.get() == ReservationLockOperation.SESSION_CANCEL
                && sessionId.equals(targetSessionId);
        }

        private void await(CountDownLatch latch) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("reservation lock order test latch timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("reservation lock order test interrupted", exception);
            }
        }
    }

    private enum ReservationLockOperation {
        HOLD_CREATE,
        RESERVATION_CONFIRM,
        SESSION_CANCEL
    }

    private enum ReservationLockTarget {
        CONTENT,
        CONTENT_SESSION
    }
}
