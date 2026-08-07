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
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.content.service.CancelContentSessionUseCase;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.dto.CancelReservationResponse;
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
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReservationCancellationUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private final ReservationCancellationUseCase reservationCancellationUseCase;
    private final CancelContentSessionUseCase cancelContentSessionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    ReservationCancellationUseCaseMySqlTest(
        ReservationCancellationUseCase reservationCancellationUseCase,
        CancelContentSessionUseCase cancelContentSessionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        AuditEventRepository auditEventRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.reservationCancellationUseCase = reservationCancellationUseCase;
        this.cancelContentSessionUseCase = cancelContentSessionUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.auditEventRepository = auditEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(
            registry,
            ReservationCancellationUseCaseMySqlTest::withUseAffectedRows
        );
    }

    @Test
    @Timeout(10)
    void 동시_취소_재시도는_모두_최초_취소_결과를_반환하고_정원을_한번만_복구한다() throws Exception {
        Fixture fixture = createFixture();

        List<CancellationResult> results = cancelConcurrently(fixture);

        assertThat(results).allSatisfy(result -> assertThat(result.errorCode()).isNull());
        assertThat(results).extracting(result -> result.response().status())
            .containsOnly(ReservationStatus.CANCELLED.name());
        assertThat(results).extracting(result -> result.response().cancelledAt())
            .containsOnly(results.getFirst().response().cancelledAt());
        assertThat(results).extracting(result -> result.response().capacityReleasedAt())
            .containsOnly(results.getFirst().response().capacityReleasedAt());
        assertThat(reservationRepository.findById(fixture.reservationId()))
            .hasValueSatisfying(reservation -> {
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(reservation.getCancelledAt()).isEqualTo(reservation.getCapacityReleasedAt());
            });
        assertThat(contentSessionRepository.findById(fixture.sessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(1));
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.RESERVATION)
            .filteredOn(event -> event.getTargetId().equals(fixture.reservationId()))
            .singleElement()
            .satisfies(event -> assertThat(event.getResult()).isEqualTo(AuditEventResult.SUCCESS));
    }

    @Test
    @Timeout(10)
    void cancelSessionAndCancelReservationConcurrently_terminalizesWithoutDeadlock() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<CancellationResult> reservationCancellation = executorService.submit(
                () -> cancelAfterStart(fixture, ready, start)
            );
            Future<ErrorCode> sessionCancellation = executorService.submit(
                () -> cancelSessionAfterStart(fixture, ready, start)
            );

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            CancellationResult reservationResult = reservationCancellation.get(5, TimeUnit.SECONDS);
            ErrorCode sessionCancellationErrorCode = sessionCancellation.get(5, TimeUnit.SECONDS);
            assertThat(reservationResult.errorCode()).isNull();
            assertThat(sessionCancellationErrorCode).isNull();
            assertThat(reservationResult.response().status()).isEqualTo(ReservationStatus.CANCELLED.name());
        }

        assertThat(reservationRepository.findById(fixture.reservationId()))
            .hasValueSatisfying(reservation ->
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED)
            );
        assertThat(contentSessionRepository.findById(fixture.sessionId()))
            .hasValueSatisfying(session -> {
                assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.CANCELLED);
                assertThat(session.getRemainingCapacity()).isEqualTo(1);
            });
    }

    private List<CancellationResult> cancelConcurrently(Fixture fixture) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<CancellationResult> first = executorService.submit(
                () -> cancelAfterStart(fixture, ready, start)
            );
            Future<CancellationResult> second = executorService.submit(
                () -> cancelAfterStart(fixture, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }
    }

    private ErrorCode cancelSessionAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            cancelContentSessionUseCase.cancel(
                fixture.operatorId(),
                fixture.sessionId(),
                "Session cancelled",
                UUID.randomUUID()
            );
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private CancellationResult cancelAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            return new CancellationResult(reservationCancellationUseCase.cancel(
                fixture.userId(),
                fixture.reservationId(),
                UUID.randomUUID()
            ), null);
        } catch (BusinessException exception) {
            return new CancellationResult(null, exception.getErrorCode());
        }
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
                "시작 전까지 취소할 수 있습니다.",
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
            session = contentSessionRepository.save(session);
            jdbcTemplate.update(
                "UPDATE content_session SET remaining_capacity = 0 WHERE session_id = ?",
                session.getSessionId()
            );
            CapacityHold capacityHold = capacityHoldRepository.save(new CapacityHold(
                region,
                session,
                user,
                1,
                CapacityHoldStatus.CONSUMED,
                now.plusSeconds(600),
                now,
                null,
                null,
                now
            ));
            Reservation reservation = reservationRepository.save(new Reservation(
                "R" + suffix,
                UUID.randomUUID().toString(),
                region,
                capacityHold,
                session,
                user,
                ReservationStatus.CONFIRMED,
                now,
                null,
                null,
                null,
                null
            ));
            return new Fixture(
                user.getUserId(),
                operator.getUserId(),
                reservation.getReservationId(),
                session.getSessionId()
            );
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent cancellation did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent cancellation was interrupted", exception);
        }
    }

    private static String withUseAffectedRows(String jdbcUrl) {
        String parameterPrefix = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + parameterPrefix + "useAffectedRows=true";
    }

    private record Fixture(Long userId, Long operatorId, Long reservationId, Long sessionId) {
    }

    private record CancellationResult(CancelReservationResponse response, ErrorCode errorCode) {
    }
}
