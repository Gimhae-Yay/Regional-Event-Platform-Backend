package io.regionevent.regioneventbackend.domain.reservation.service;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
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
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReservationConfirmationUseCaseMySqlTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    private final ReservationConfirmationUseCase reservationConfirmationUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AuditEventRepository auditEventRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    ReservationConfirmationUseCaseMySqlTest(
        ReservationConfirmationUseCase reservationConfirmationUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        IdempotencyRecordRepository idempotencyRecordRepository,
        AuditEventRepository auditEventRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.reservationConfirmationUseCase = reservationConfirmationUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.auditEventRepository = auditEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> withUseAffectedRows(MYSQL.getJdbcUrl()));
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
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

    private ReservationConfirmationResult confirm(Fixture fixture, Long holdId, String idempotencyKey) {
        return reservationConfirmationUseCase.confirm(
            fixture.user().getUserId(),
            holdId.toString(),
            idempotencyKey,
            UUID.randomUUID()
        );
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
            contentSessionRepository.save(session);
            CapacityHold capacityHold = capacityHoldRepository.save(new CapacityHold(
                region,
                session,
                user,
                1,
                CapacityHoldStatus.ACTIVE,
                now.plusSeconds(600),
                null,
                null,
                null,
                now
            ));
            return new Fixture(user, capacityHold);
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

    private record Fixture(AppUser user, CapacityHold capacityHold) {
    }
}
