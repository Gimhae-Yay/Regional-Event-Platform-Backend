package io.regionevent.regioneventbackend.domain.visit.service;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;
import io.regionevent.regioneventbackend.domain.idempotency.repository.IdempotencyRecordRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.visit.dto.ManualCheckInRequest;
import io.regionevent.regioneventbackend.domain.visit.dto.QrCheckInRequest;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.qr.QrTokenService;
import io.regionevent.regioneventbackend.support.mysql.AffectedRowsLockTimeoutOneMySqlTestSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CheckInUseCaseMySqlTest extends AffectedRowsLockTimeoutOneMySqlTestSupport {

    private final CheckInUseCase checkInUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final QrTokenService qrTokenService;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    CheckInUseCaseMySqlTest(
        CheckInUseCase checkInUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
        IdempotencyRecordRepository idempotencyRecordRepository,
        QrTokenService qrTokenService,
        PlatformTransactionManager transactionManager
    ) {
        this.checkInUseCase = checkInUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.visitRepository = visitRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.qrTokenService = qrTokenService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    @Timeout(10)
    void qrCheckIn_withSameKeyConcurrently_returnsStoredResultOrInProgressWithoutDuplicateVisit() throws Exception {
        Fixture fixture = createFixture();
        String idempotencyKey = "qr-same-key-" + System.nanoTime();

        List<CheckInResult> results = checkInByQrConcurrently(
            fixture,
            idempotencyKey,
            idempotencyKey
        );

        assertThat(results)
            .filteredOn(CheckInResult::isSuccessful)
            .isNotEmpty();
        assertThat(results)
            .filteredOn(result -> !result.isSuccessful())
            .allSatisfy(result -> assertThat(result.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS));
        assertThat(results)
            .filteredOn(CheckInResult::isSuccessful)
            .extracting(result -> result.response().visitId())
            .containsOnly(successfulVisitId(results));
        assertSingleVisitForReservation(fixture.reservationId());
        assertThat(idempotencyRecordRepository.findAll())
            .singleElement()
            .satisfies(record -> {
                assertThat(record.getStatus()).isEqualTo(IdempotencyRecordStatus.SUCCEEDED);
                assertThat(record.getResultVisit()).isNotNull();
            });
    }

    @Test
    @Timeout(10)
    void qrCheckIn_withDifferentKeysConcurrently_returnsSameVisitWithoutDuplicateVisit() throws Exception {
        Fixture fixture = createFixture();

        List<CheckInResult> results = checkInByQrConcurrently(
            fixture,
            "qr-first-key-" + System.nanoTime(),
            "qr-second-key-" + System.nanoTime()
        );

        assertThat(results).allSatisfy(result -> assertThat(result.isSuccessful()).isTrue());
        assertThat(results)
            .extracting(result -> result.response().visitId())
            .containsOnly(results.getFirst().response().visitId());
        assertSingleVisitForReservation(fixture.reservationId());
        assertThat(idempotencyRecordRepository.findAll())
            .hasSize(2)
            .allSatisfy(record -> {
                assertThat(record.getStatus()).isEqualTo(IdempotencyRecordStatus.SUCCEEDED);
                assertThat(record.getResultVisit().getVisitId().toString()).isEqualTo(results.getFirst().response().visitId());
            });
    }

    @Test
    @Timeout(10)
    void manualCheckIn_withDifferentKeysConcurrently_createsSingleVisitAndReturnsConflict() throws Exception {
        Fixture fixture = createFixture();

        List<CheckInResult> results = checkInManuallyConcurrently(
            fixture,
            "manual-first-key-" + System.nanoTime(),
            "manual-second-key-" + System.nanoTime()
        );

        assertThat(results).filteredOn(CheckInResult::isSuccessful).hasSize(1);
        assertThat(results)
            .filteredOn(result -> !result.isSuccessful())
            .singleElement()
            .satisfies(result -> assertThat(result.errorCode()).isEqualTo(ErrorCode.CHECK_IN_CONFLICT));
        assertSingleVisitForReservation(fixture.reservationId());
        assertThat(idempotencyRecordRepository.findAll())
            .hasSize(2)
            .extracting(record -> record.getStatus())
            .containsExactlyInAnyOrder(IdempotencyRecordStatus.SUCCEEDED, IdempotencyRecordStatus.FAILED);
    }

    @Test
    @Timeout(10)
    void manualCheckIn_withSameKeyConcurrently_returnsStoredResultOrInProgressWithoutDuplicateVisit()
        throws Exception {

        Fixture fixture = createFixture();
        String idempotencyKey = "manual-same-key-" + System.nanoTime();

        List<CheckInResult> results = checkInManuallyConcurrently(
            fixture,
            idempotencyKey,
            idempotencyKey
        );

        assertThat(results)
            .filteredOn(CheckInResult::isSuccessful)
            .isNotEmpty();
        assertThat(results)
            .filteredOn(result -> !result.isSuccessful())
            .allSatisfy(result -> assertThat(result.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS));
        assertThat(results)
            .filteredOn(CheckInResult::isSuccessful)
            .extracting(result -> result.response().visitId())
            .containsOnly(successfulVisitId(results));
        assertSingleVisitForReservation(fixture.reservationId());
        assertThat(idempotencyRecordRepository.findAll())
            .singleElement()
            .satisfies(record -> {
                assertThat(record.getStatus()).isEqualTo(IdempotencyRecordStatus.SUCCEEDED);
                assertThat(record.getResultVisit()).isNotNull();
            });
    }

    @Test
    @Timeout(10)
    void checkInByQrAndManually_withSameKeyConcurrently_returnsConflictOrInProgressWithoutDeadlock()
        throws Exception {

        Fixture fixture = createFixture();
        String idempotencyKey = "mixed-same-key-" + System.nanoTime();

        List<CheckInResult> results = checkInConcurrently(
            () -> checkInUseCase.checkInByQr(
                fixture.operatorUserId(),
                new QrCheckInRequest(fixture.qrToken()),
                idempotencyKey,
                UUID.randomUUID()
            ),
            () -> checkInUseCase.checkInManually(
                fixture.operatorUserId(),
                new ManualCheckInRequest(fixture.reservationNo(), ManualCheckInReason.QR_SCAN_FAILED.name()),
                idempotencyKey,
                UUID.randomUUID()
            )
        );

        assertThat(results).filteredOn(CheckInResult::isSuccessful).hasSize(1);
        assertThat(results)
            .filteredOn(result -> !result.isSuccessful())
            .singleElement()
            .satisfies(result -> assertThat(result.errorCode()).isIn(
                ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS
            ));
        assertSingleVisitForReservation(fixture.reservationId());
        assertThat(idempotencyRecordRepository.findAll())
            .singleElement()
            .satisfies(record -> {
                assertThat(record.getStatus()).isEqualTo(IdempotencyRecordStatus.SUCCEEDED);
                assertThat(record.getResultVisit()).isNotNull();
            });
    }

    private List<CheckInResult> checkInByQrConcurrently(
        Fixture fixture,
        String firstIdempotencyKey,
        String secondIdempotencyKey
    ) throws Exception {
        return checkInConcurrently(
            () -> checkInUseCase.checkInByQr(
                fixture.operatorUserId(),
                new QrCheckInRequest(fixture.qrToken()),
                firstIdempotencyKey,
                UUID.randomUUID()
            ),
            () -> checkInUseCase.checkInByQr(
                fixture.operatorUserId(),
                new QrCheckInRequest(fixture.qrToken()),
                secondIdempotencyKey,
                UUID.randomUUID()
            )
        );
    }

    private List<CheckInResult> checkInManuallyConcurrently(
        Fixture fixture,
        String firstIdempotencyKey,
        String secondIdempotencyKey
    ) throws Exception {
        return checkInConcurrently(
            () -> checkInUseCase.checkInManually(
                fixture.operatorUserId(),
                new ManualCheckInRequest(fixture.reservationNo(), ManualCheckInReason.QR_SCAN_FAILED.name()),
                firstIdempotencyKey,
                UUID.randomUUID()
            ),
            () -> checkInUseCase.checkInManually(
                fixture.operatorUserId(),
                new ManualCheckInRequest(fixture.reservationNo(), ManualCheckInReason.QR_SCAN_FAILED.name()),
                secondIdempotencyKey,
                UUID.randomUUID()
            )
        );
    }

    private List<CheckInResult> checkInConcurrently(
        CheckInTask firstTask,
        CheckInTask secondTask
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<CheckInResult> first = executorService.submit(() -> checkInAfterStart(firstTask, ready, start));
            Future<CheckInResult> second = executorService.submit(() -> checkInAfterStart(secondTask, ready, start));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }
    }

    private CheckInResult checkInAfterStart(
        CheckInTask task,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return task.checkIn();
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.save(new Region("R" + suffix, "Gimhae", true));
            AppUser operator = appUserRepository.save(new AppUser(
                "operator-" + suffix + "@example.com",
                "hashed-password",
                "operator",
                "010-1111-2222",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
            AppUser user = appUserRepository.save(new AppUser(
                "visitor-" + suffix + "@example.com",
                "hashed-password",
                "visitor",
                "010-3333-4444",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(user, UserRole.VISITOR, null));
            Content content = contentRepository.save(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PUBLISHED,
                "Check-in event",
                "Check-in event description",
                "Gimhae",
                "10:00-18:00",
                "055-123-4567",
                "Follow instructions",
                "Age 7+",
                "Limited parking",
                "Cancellation policy",
                now.minusSeconds(600)
            ));
            ContentSession session = new ContentSession(
                content,
                region,
                now.minusSeconds(600),
                now.plusSeconds(3_600),
                now.minusSeconds(60),
                now.plusSeconds(600),
                10
            );
            session.approve(operator, now.minusSeconds(300));
            ContentSession savedSession = contentSessionRepository.save(session);
            CapacityHold capacityHold = capacityHoldRepository.save(new CapacityHold(
                region,
                savedSession,
                user,
                1,
                CapacityHoldStatus.CONSUMED,
                now,
                now,
                null,
                null
            ));
            Reservation reservation = reservationRepository.save(new Reservation(
                "R-" + suffix,
                UUID.randomUUID().toString(),
                region,
                capacityHold,
                savedSession,
                user,
                ReservationStatus.CONFIRMED,
                now,
                null,
                null,
                null,
                null
            ));
            QrTokenService.IssuedQrToken qrToken = qrTokenService.issue(
                reservation.getQrReference(),
                savedSession.getSessionId(),
                now,
                savedSession.getCheckinCloseAt()
            );
            return new Fixture(
                operator.getUserId(),
                reservation.getReservationId(),
                reservation.getReservationNo(),
                qrToken.token()
            );
        });
    }

    private void assertSingleVisitForReservation(Long reservationId) {
        transactionTemplate.executeWithoutResult(status -> assertThat(visitRepository.findAll())
            .filteredOn(visit -> visit.getReservation().getReservationId().equals(reservationId))
            .hasSize(1));
    }

    private String successfulVisitId(List<CheckInResult> results) {
        return results.stream()
            .filter(CheckInResult::isSuccessful)
            .findFirst()
            .orElseThrow()
            .response()
            .visitId();
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

    @FunctionalInterface
    private interface CheckInTask {

        CheckInResult checkIn();
    }

    private record Fixture(
        Long operatorUserId,
        Long reservationId,
        String reservationNo,
        String qrToken
    ) {
    }
}
