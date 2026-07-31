package io.regionevent.regioneventbackend.domain.idempotency.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyOperation;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecord;
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
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    IdempotencyConfiguration.class,
    IdempotencyService.class,
    IdempotencyServiceMySqlTest.ClockConfiguration.class
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdempotencyServiceMySqlTest {

    private static final Instant NOW = Instant.parse("2037-08-02T00:00:00Z");
    private static final Instant EXPIRED_AT = Instant.parse("2000-01-01T00:00:00Z");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    private final IdempotencyService idempotencyService;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AppUserRepository appUserRepository;
    private final RegionRepository regionRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    IdempotencyServiceMySqlTest(
        IdempotencyService idempotencyService,
        IdempotencyRecordRepository idempotencyRecordRepository,
        AppUserRepository appUserRepository,
        RegionRepository regionRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.idempotencyService = idempotencyService;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.appUserRepository = appUserRepository;
        this.regionRepository = regionRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.visitRepository = visitRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> withUseAffectedRows(MYSQL.getJdbcUrl()));
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("idempotency.retention", () -> "PT24H");
        registry.add("idempotency.cleanup-fixed-delay", () -> "PT1H");
        registry.add("idempotency.cleanup-initial-delay", () -> "PT1H");
        registry.add("idempotency.lock-wait-timeout-seconds", () -> "1");
    }

    private static String withUseAffectedRows(String jdbcUrl) {
        String parameterPrefix = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + parameterPrefix + "useAffectedRows=true";
    }

    @Test
    void 같은_요청은_성공_결과를_재사용하고_affected_rows_설정에_의존하지_않는다() {
        ReservationFixture fixture = createReservationFixture();
        IdempotencyCommand command = command(fixture.actor(), "success-key", "success-request");

        IdempotencyAcquireResult firstResult = inTransaction(() -> {
            IdempotencyAcquireResult result = idempotencyService.acquire(command);
            IdempotencyRecord record = ((IdempotencyAcquireResult.Acquired) result).record();
            idempotencyService.completeWithReservation(record, "RESERVATION_CONFIRMED", fixture.reservation());
            return result;
        });
        IdempotencyAcquireResult retryResult = inTransaction(() -> idempotencyService.acquire(command));

        assertThat(firstResult).isInstanceOf(IdempotencyAcquireResult.Acquired.class);
        assertThat(retryResult).isInstanceOf(IdempotencyAcquireResult.Succeeded.class);
        IdempotencyRecord savedRecord = ((IdempotencyAcquireResult.Succeeded) retryResult).record();
        assertThat(savedRecord.getResultCode()).isEqualTo("RESERVATION_CONFIRMED");
        assertThat(savedRecord.getResultReservation().getReservationId())
            .isEqualTo(fixture.reservation().getReservationId());
        assertThat(savedRecord.getCompletedAt()).isEqualTo(NOW);
        assertThat(savedRecord.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void 체크인_성공_결과를_재사용한다() {
        ReservationFixture fixture = createReservationFixture();
        Visit visit = createVisit(fixture);
        IdempotencyCommand command = checkInCommand(fixture.actor(), "check-in-key", "check-in-request");

        inTransaction(() -> {
            IdempotencyAcquireResult.Acquired acquired =
                (IdempotencyAcquireResult.Acquired) idempotencyService.acquire(command);
            idempotencyService.completeWithVisit(acquired.record(), "CHECKED_IN", visit);
            return null;
        });
        IdempotencyAcquireResult retryResult = inTransaction(() -> idempotencyService.acquire(command));

        assertThat(retryResult).isInstanceOf(IdempotencyAcquireResult.Succeeded.class);
        IdempotencyRecord savedRecord = ((IdempotencyAcquireResult.Succeeded) retryResult).record();
        assertThat(savedRecord.getOperation()).isEqualTo(IdempotencyOperation.CHECK_IN);
        assertThat(savedRecord.getResultCode()).isEqualTo("CHECKED_IN");
        assertThat(savedRecord.getResultVisit().getVisitId()).isEqualTo(visit.getVisitId());
    }

    @Test
    void 다른_요청_의미는_키_충돌로_구분하고_결정적_실패를_재사용한다() {
        AppUser actor = createActor();
        IdempotencyCommand command = command(actor, "failure-key", "first-request");

        inTransaction(() -> {
            IdempotencyAcquireResult.Acquired acquired =
                (IdempotencyAcquireResult.Acquired) idempotencyService.acquire(command);
            idempotencyService.completeWithFailure(acquired.record(), "RESERVATION_CONFIRM_CONFLICT");
            return null;
        });
        IdempotencyAcquireResult sameRequestResult = inTransaction(() -> idempotencyService.acquire(command));
        IdempotencyAcquireResult differentRequestResult = inTransaction(
            () -> idempotencyService.acquire(command(actor, "failure-key", "other-request"))
        );

        assertThat(sameRequestResult).isInstanceOf(IdempotencyAcquireResult.Failed.class);
        assertThat(((IdempotencyAcquireResult.Failed) sameRequestResult).record().getResultCode())
            .isEqualTo("RESERVATION_CONFIRM_CONFLICT");
        assertThat(differentRequestResult).isInstanceOf(IdempotencyAcquireResult.KeyConflict.class);
    }

    @Test
    void 롤백된_점유는_남기지_않아_다음_요청이_새로_점유한다() {
        AppUser actor = createActor();
        IdempotencyCommand command = command(actor, "rollback-key", "rollback-request");

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(idempotencyService.acquire(command)).isInstanceOf(IdempotencyAcquireResult.Acquired.class);
            status.setRollbackOnly();
        });
        IdempotencyAcquireResult retryResult = inTransaction(() -> idempotencyService.acquire(command));

        assertThat(retryResult).isInstanceOf(IdempotencyAcquireResult.Acquired.class);
    }

    @Test
    void 외부_트랜잭션_없이_점유할_수_없다() {
        AppUser actor = createActor();

        assertThatThrownBy(() -> idempotencyService.acquire(command(actor, "mandatory-key", "mandatory-request")))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void 점유_뒤에_MySQL_세션_잠금_대기_제한을_복원한다() {
        AppUser actor = createActor();

        int originalLockWaitTimeout = inTransaction(this::findMySqlLockWaitTimeout);
        int restoredLockWaitTimeout = inTransaction(() -> {
            idempotencyService.acquire(command(actor, "lock-timeout-key", "lock-timeout-request"));
            return findMySqlLockWaitTimeout();
        });

        assertThat(restoredLockWaitTimeout).isEqualTo(originalLockWaitTimeout);
    }

    @Test
    @Timeout(10)
    void 동일_키_동시_경합은_도메인_처리_없이_처리_중으로_응답한다() throws Exception {
        AppUser actor = createActor();
        IdempotencyCommand command = command(actor, "concurrent-key", "concurrent-request");
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<?> firstRequest = executorService.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                assertThat(idempotencyService.acquire(command)).isInstanceOf(IdempotencyAcquireResult.Acquired.class);
                acquired.countDown();
                await(release);
                IdempotencyRecord record = idempotencyRecordRepository
                    .findByActorUserIdAndOperationAndIdempotencyKeyHash(
                        actor.getUserId(),
                        IdempotencyOperation.RESERVATION_CONFIRM,
                        "concurrent-key"
                    )
                    .orElseThrow();
                idempotencyService.completeWithFailure(record, "RESERVATION_CONFIRM_CONFLICT");
            }));
            assertThat(acquired.await(3, TimeUnit.SECONDS)).isTrue();

            Future<IdempotencyAcquireResult> secondRequest = executorService.submit(
                () -> inTransaction(() -> idempotencyService.acquire(command))
            );
            IdempotencyAcquireResult secondResult = secondRequest.get(5, TimeUnit.SECONDS);
            release.countDown();
            firstRequest.get(5, TimeUnit.SECONDS);

            assertThat(secondResult).isInstanceOf(IdempotencyAcquireResult.InProgress.class);
        }
    }

    @Test
    void 만료된_종결_기록만_정리하고_처리_중_기록은_보존한다() {
        AppUser actor = createActor();
        IdempotencyCommand failedCommand = command(actor, "expired-failed-key", "failed-request");
        IdempotencyCommand processingCommand = command(actor, "processing-key", "processing-request");

        inTransaction(() -> {
            IdempotencyAcquireResult.Acquired acquired =
                (IdempotencyAcquireResult.Acquired) idempotencyService.acquire(failedCommand);
            idempotencyService.completeWithFailure(acquired.record(), "RESERVATION_CONFIRM_CONFLICT");
            idempotencyService.acquire(processingCommand);
            return null;
        });
        inTransaction(() -> {
            jdbcTemplate.update(
                "UPDATE idempotency_record SET expires_at = ? WHERE actor_user_id = ? AND idempotency_key_hash = ?",
                Timestamp.from(EXPIRED_AT),
                actor.getUserId(),
                "expired-failed-key"
            );
            return null;
        });

        int deletedCount = inTransaction(idempotencyService::deleteExpiredTerminalRecords);

        assertThat(deletedCount).isOne();
        assertThat(idempotencyRecordRepository.findByActorUserIdAndOperationAndIdempotencyKeyHash(
            actor.getUserId(),
            IdempotencyOperation.RESERVATION_CONFIRM,
            "expired-failed-key"
        )).isEmpty();
        assertThat(idempotencyRecordRepository.findByActorUserIdAndOperationAndIdempotencyKeyHash(
            actor.getUserId(),
            IdempotencyOperation.RESERVATION_CONFIRM,
            "processing-key"
        )).hasValueSatisfying(record -> assertThat(record.getStatus()).isEqualTo(IdempotencyRecordStatus.PROCESSING));
    }

    @Test
    void 데이터베이스_현재_시각보다_이르지_않은_종결_기록은_정리하지_않는다() {
        AppUser actor = createActor();
        IdempotencyCommand command = command(actor, "database-clock-key", "database-clock-request");

        inTransaction(() -> {
            IdempotencyAcquireResult.Acquired acquired =
                (IdempotencyAcquireResult.Acquired) idempotencyService.acquire(command);
            idempotencyService.completeWithFailure(acquired.record(), "RESERVATION_CONFIRM_CONFLICT");
            return null;
        });
        inTransaction(() -> {
            jdbcTemplate.update(
                "UPDATE idempotency_record SET expires_at = ? WHERE actor_user_id = ? AND idempotency_key_hash = ?",
                Timestamp.from(NOW.minusSeconds(1)),
                actor.getUserId(),
                "database-clock-key"
            );
            return null;
        });

        int deletedCount = inTransaction(idempotencyService::deleteExpiredTerminalRecords);

        assertThat(deletedCount).isZero();
        assertThat(idempotencyRecordRepository.findByActorUserIdAndOperationAndIdempotencyKeyHash(
            actor.getUserId(),
            IdempotencyOperation.RESERVATION_CONFIRM,
            "database-clock-key"
        )).isPresent();
    }

    private AppUser createActor() {
        return inTransaction(() -> appUserRepository.saveAndFlush(new AppUser(
            "actor-" + System.nanoTime() + "@example.com",
            "hashed-password",
            "예약 사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        )));
    }

    private ReservationFixture createReservationFixture() {
        return inTransaction(() -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
            AppUser actor = appUserRepository.saveAndFlush(new AppUser(
                "reservation-actor-" + suffix + "@example.com",
                "hashed-password",
                "예약 사용자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            AppUser operator = appUserRepository.saveAndFlush(new AppUser(
                "reservation-operator-" + suffix + "@example.com",
                "hashed-password",
                "운영자",
                "010-9876-5432",
                AppUserStatus.ACTIVE
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
                NOW
            ));
            ContentSession session = new ContentSession(
                content,
                region,
                NOW.plusSeconds(3_600),
                NOW.plusSeconds(10_800),
                NOW.plusSeconds(1_800),
                NOW.plusSeconds(9_000),
                20
            );
            session.approve(operator, NOW);
            session = contentSessionRepository.saveAndFlush(session);
            CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
                region,
                session,
                actor,
                1,
                CapacityHoldStatus.CONSUMED,
                NOW,
                NOW.plusSeconds(300),
                null,
                null
            ));
            Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
                "R-" + suffix,
                "qr-reference-" + suffix,
                region,
                hold,
                session,
                actor,
                ReservationStatus.CONFIRMED,
                NOW,
                null,
                null,
                null,
                null
            ));
            return new ReservationFixture(actor, operator, region, content, session, reservation);
        });
    }

    private Visit createVisit(ReservationFixture fixture) {
        return inTransaction(() -> visitRepository.saveAndFlush(new Visit(
            fixture.region(),
            fixture.reservation(),
            fixture.actor(),
            fixture.content(),
            fixture.session(),
            fixture.operator(),
            CheckinMethod.QR,
            NOW
        )));
    }

    private IdempotencyCommand command(AppUser actor, String idempotencyKeyHash, String requestHash) {
        return new IdempotencyCommand(
            actor,
            IdempotencyOperation.RESERVATION_CONFIRM,
            idempotencyKeyHash,
            requestHash
        );
    }

    private IdempotencyCommand checkInCommand(AppUser actor, String idempotencyKeyHash, String requestHash) {
        return new IdempotencyCommand(
            actor,
            IdempotencyOperation.CHECK_IN,
            idempotencyKeyHash,
            requestHash
        );
    }

    private int findMySqlLockWaitTimeout() {
        return jdbcTemplate.queryForObject("SELECT @@SESSION.innodb_lock_wait_timeout", Integer.class);
    }

    private <T> T inTransaction(TransactionalSupplier<T> supplier) {
        return transactionTemplate.execute(status -> supplier.get());
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
    private interface TransactionalSupplier<T> {

        T get();
    }

    private record ReservationFixture(
        AppUser actor,
        AppUser operator,
        Region region,
        Content content,
        ContentSession session,
        Reservation reservation
    ) {
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
