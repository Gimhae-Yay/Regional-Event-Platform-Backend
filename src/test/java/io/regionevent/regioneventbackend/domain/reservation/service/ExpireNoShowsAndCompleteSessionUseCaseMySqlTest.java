package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActorLinkService;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventService;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
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

@SpringBootTest(properties = "reservation.no-show-completion.initial-delay=PT24H")
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ExpireNoShowsAndCompleteSessionUseCaseMySqlTest.FailingAuditEventUseCaseConfig.class)
class ExpireNoShowsAndCompleteSessionUseCaseMySqlTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    private final ExpireNoShowsAndCompleteSessionUseCase useCase;
    private final ReservationRepository reservationRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentRepository contentRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final AppUserRepository appUserRepository;
    private final RegionRepository regionRepository;
    private final TransactionTemplate transactionTemplate;
    private final FailingRecordAuditEventUseCase failingRecordAuditEventUseCase;

    @Autowired
    ExpireNoShowsAndCompleteSessionUseCaseMySqlTest(
        ExpireNoShowsAndCompleteSessionUseCase useCase,
        ReservationRepository reservationRepository,
        CapacityHoldRepository capacityHoldRepository,
        ContentSessionRepository contentSessionRepository,
        ContentRepository contentRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        AppUserRepository appUserRepository,
        RegionRepository regionRepository,
        PlatformTransactionManager transactionManager,
        FailingRecordAuditEventUseCase failingRecordAuditEventUseCase
    ) {
        this.useCase = useCase;
        this.reservationRepository = reservationRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.contentRepository = contentRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.appUserRepository = appUserRepository;
        this.regionRepository = regionRepository;
        transactionTemplate = new TransactionTemplate(transactionManager);
        this.failingRecordAuditEventUseCase = failingRecordAuditEventUseCase;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @AfterEach
    void cleanUp() {
        failingRecordAuditEventUseCase.resetFailureInjection();
        auditEventActorLinkRepository.deleteAllInBatch();
        auditEventRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        capacityHoldRepository.deleteAllInBatch();
        contentSessionRepository.deleteAllInBatch();
        contentRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
        regionRepository.deleteAllInBatch();
    }

    @Test
    void 종료와_체크인마감이_지난_회차는_확정예약만_노쇼로_전환하고_회차를_완료한다() {
        Fixture fixture = createFixture(
            Instant.now().minusSeconds(7_200),
            Instant.now().minusSeconds(3_600),
            List.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN, ReservationStatus.CANCELLED)
        );

        NoShowAndSessionCompletionResult result = useCase.execute();

        Reservation expiredReservation = reservationRepository.findById(fixture.reservationIds().getFirst())
            .orElseThrow();
        Reservation checkedInReservation = reservationRepository.findById(fixture.reservationIds().get(1))
            .orElseThrow();
        Reservation cancelledReservation = reservationRepository.findById(fixture.reservationIds().get(2))
            .orElseThrow();
        ContentSession completedSession = contentSessionRepository.findById(fixture.sessionId()).orElseThrow();

        assertThat(result.expiredReservationCount()).isOne();
        assertThat(result.completedSessionCount()).isOne();
        assertThat(result.failedSessionCount()).isZero();
        assertThat(expiredReservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(expiredReservation.getExpiredAt()).isNotNull();
        assertThat(expiredReservation.getCapacityReleasedAt()).isNull();
        assertThat(checkedInReservation.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThat(cancelledReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(completedSession.getStatus()).isEqualTo(ContentSessionStatus.COMPLETED);
        assertThat(completedSession.getCompletedAt()).isNotNull();
        assertThat(completedSession.getRemainingCapacity()).isEqualTo(fixture.remainingCapacity());
        assertNoShowAndCompletionAuditEvents(fixture, expiredReservation, completedSession);
    }

    @Test
    void 체크인마감이_지나지_않으면_노쇼와_회차완료를_수행하지_않는다() {
        Fixture fixture = createFixture(
            Instant.now().minusSeconds(3_600),
            Instant.now().plusSeconds(3_600),
            List.of(ReservationStatus.CONFIRMED)
        );

        NoShowAndSessionCompletionResult result = useCase.execute();

        assertThat(result.expiredReservationCount()).isZero();
        assertThat(result.completedSessionCount()).isZero();
        assertThat(reservationRepository.findById(fixture.reservationIds().getFirst()))
            .hasValueSatisfying(reservation -> {
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
                assertThat(reservation.getExpiredAt()).isNull();
            });
        assertThat(contentSessionRepository.findById(fixture.sessionId()))
            .hasValueSatisfying(session -> {
                assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.SCHEDULED);
                assertThat(session.getCompletedAt()).isNull();
            });
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void 예약이_없는_종료회차도_완료하고_감사를_남긴다() {
        Fixture fixture = createFixture(
            Instant.now().minusSeconds(7_200),
            Instant.now().minusSeconds(3_600),
            List.of()
        );

        NoShowAndSessionCompletionResult result = useCase.execute();

        assertThat(result.expiredReservationCount()).isZero();
        assertThat(result.completedSessionCount()).isOne();
        assertThat(contentSessionRepository.findById(fixture.sessionId()))
            .hasValueSatisfying(session -> assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.COMPLETED));
        assertThat(auditEventRepository.findAll())
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT_SESSION);
                assertThat(auditEvent.getTargetId()).isEqualTo(fixture.sessionId());
                assertThat(auditEvent.getPreviousState()).isEqualTo("SCHEDULED");
                assertThat(auditEvent.getNextState()).isEqualTo("COMPLETED");
                assertThat(auditEvent.getActorKind()).isEqualTo("SYSTEM");
            });
    }

    @Test
    void 감사기록에_실패하면_회차단위_노쇼전환과_완료를_함께_롤백한다() {
        Fixture fixture = createFixture(
            Instant.now().minusSeconds(7_200),
            Instant.now().minusSeconds(3_600),
            List.of(ReservationStatus.CONFIRMED)
        );
        failingRecordAuditEventUseCase.failNextRecord();

        NoShowAndSessionCompletionResult failedResult = useCase.execute();

        assertThat(failedResult.expiredReservationCount()).isZero();
        assertThat(failedResult.completedSessionCount()).isZero();
        assertThat(failedResult.failedSessionCount()).isOne();
        assertThat(reservationRepository.findById(fixture.reservationIds().getFirst()))
            .hasValueSatisfying(reservation -> assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED));
        assertThat(contentSessionRepository.findById(fixture.sessionId()))
            .hasValueSatisfying(session -> assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.SCHEDULED));
        assertThat(auditEventRepository.count()).isZero();

        NoShowAndSessionCompletionResult retryResult = useCase.execute();

        assertThat(retryResult.expiredReservationCount()).isOne();
        assertThat(retryResult.completedSessionCount()).isOne();
        assertThat(retryResult.failedSessionCount()).isZero();
    }

    @Test
    @Timeout(10)
    void 중복스케줄러실행에서도_노쇼와_회차완료감사는_각각_한번만_남긴다() throws Exception {
        Fixture fixture = createFixture(
            Instant.now().minusSeconds(7_200),
            Instant.now().minusSeconds(3_600),
            List.of(ReservationStatus.CONFIRMED)
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<NoShowAndSessionCompletionResult> results;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<NoShowAndSessionCompletionResult> first = executorService.submit(
                () -> executeAfterStart(ready, start)
            );
            Future<NoShowAndSessionCompletionResult> second = executorService.submit(
                () -> executeAfterStart(ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }

        assertThat(results).extracting(NoShowAndSessionCompletionResult::failedSessionCount)
            .containsOnly(0);
        assertThat(results.stream().mapToInt(NoShowAndSessionCompletionResult::expiredReservationCount).sum())
            .isOne();
        assertThat(results.stream().mapToInt(NoShowAndSessionCompletionResult::completedSessionCount).sum())
            .isOne();
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> auditEvent.getTargetId().equals(fixture.reservationIds().getFirst()))
            .singleElement()
            .satisfies(auditEvent -> assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.RESERVATION));
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> auditEvent.getTargetId().equals(fixture.sessionId()))
            .singleElement()
            .satisfies(auditEvent -> assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT_SESSION));
    }

    private NoShowAndSessionCompletionResult executeAfterStart(
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return useCase.execute();
    }

    private Fixture createFixture(
        Instant endsAt,
        Instant checkinCloseAt,
        List<ReservationStatus> reservationStatuses
    ) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant startsAt = endsAt.minusSeconds(3_600);
            Instant checkinOpenAt = startsAt.minusSeconds(1_800);
            Region region = regionRepository.save(new Region("R" + suffix, "김해시", true));
            AppUser operator = appUserRepository.save(new AppUser(
                "operator-" + suffix + "@example.com",
                "hashed-password",
                "운영자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            AppUser visitor = appUserRepository.save(new AppUser(
                "visitor-" + suffix + "@example.com",
                "hashed-password",
                "방문자",
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
                "055-1234-5678",
                "안전요원의 안내를 따라주세요.",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소할 수 있습니다.",
                startsAt.minusSeconds(3_600)
            ));
            ContentSession session = new ContentSession(
                content,
                region,
                startsAt,
                endsAt,
                checkinOpenAt,
                checkinCloseAt,
                10
            );
            session.approve(operator, startsAt.minusSeconds(3_600));
            ContentSession savedSession = contentSessionRepository.save(session);
            List<Long> reservationIds = new ArrayList<>();
            for (int index = 0; index < reservationStatuses.size(); index++) {
                reservationIds.add(saveReservation(
                    suffix,
                    index,
                    reservationStatuses.get(index),
                    region,
                    savedSession,
                    visitor,
                    startsAt
                ).getReservationId());
            }
            return new Fixture(savedSession.getSessionId(), reservationIds, savedSession.getRemainingCapacity());
        });
    }

    private Reservation saveReservation(
        String suffix,
        int index,
        ReservationStatus status,
        Region region,
        ContentSession contentSession,
        AppUser visitor,
        Instant confirmedAt
    ) {
        CapacityHold capacityHold = capacityHoldRepository.save(new CapacityHold(
            region,
            contentSession,
            visitor,
            1,
            CapacityHoldStatus.CONSUMED,
            confirmedAt,
            confirmedAt,
            null,
            null,
            confirmedAt.minusSeconds(60)
        ));
        Instant cancelledAt = status == ReservationStatus.CANCELLED ? confirmedAt.plusSeconds(60) : null;
        String cancellationReason = status == ReservationStatus.CANCELLED ? "USER_CANCELLED" : null;
        Instant expiredAt = status == ReservationStatus.EXPIRED ? confirmedAt.plusSeconds(60) : null;
        return reservationRepository.save(new Reservation(
            "R-" + suffix + "-" + index,
            "qr-" + suffix + "-" + index,
            region,
            capacityHold,
            contentSession,
            visitor,
            status,
            confirmedAt,
            cancelledAt,
            cancellationReason,
            expiredAt,
            null
        ));
    }

    private void assertNoShowAndCompletionAuditEvents(
        Fixture fixture,
        Reservation expiredReservation,
        ContentSession completedSession
    ) {
        List<AuditEvent> auditEvents = auditEventRepository.findAll();

        assertThat(auditEvents)
            .filteredOn(auditEvent -> auditEvent.getTargetId().equals(expiredReservation.getReservationId()))
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.RESERVATION);
                assertThat(auditEvent.getPreviousState()).isEqualTo("CONFIRMED");
                assertThat(auditEvent.getNextState()).isEqualTo("EXPIRED");
                assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
                assertThat(auditEvent.getReasonCode()).isEqualTo("NO_SHOW");
                assertThat(auditEvent.getActorKind()).isEqualTo("SYSTEM");
                assertThat(auditEvent.getActorRole()).isNull();
                assertThat(auditEvent.getOccurredAt()).isEqualTo(expiredReservation.getExpiredAt());
            });
        assertThat(auditEvents)
            .filteredOn(auditEvent -> auditEvent.getTargetId().equals(fixture.sessionId()))
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT_SESSION);
                assertThat(auditEvent.getPreviousState()).isEqualTo("SCHEDULED");
                assertThat(auditEvent.getNextState()).isEqualTo("COMPLETED");
                assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
                assertThat(auditEvent.getOccurredAt()).isEqualTo(completedSession.getCompletedAt());
            });
        assertThat(auditEventActorLinkRepository.count()).isZero();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent scheduler execution did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent scheduler execution was interrupted", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingAuditEventUseCaseConfig {

        @Bean
        @Primary
        FailingRecordAuditEventUseCase failingRecordAuditEventUseCase(
            AuditEventService auditEventService,
            AuditEventActorLinkService auditEventActorLinkService
        ) {
            return new FailingRecordAuditEventUseCase(auditEventService, auditEventActorLinkService);
        }
    }

    static class FailingRecordAuditEventUseCase extends RecordAuditEventUseCase {

        private final AtomicBoolean failNextRecord = new AtomicBoolean();

        FailingRecordAuditEventUseCase(
            AuditEventService auditEventService,
            AuditEventActorLinkService auditEventActorLinkService
        ) {
            super(auditEventService, auditEventActorLinkService);
        }

        @Override
        public AuditEvent record(AuditEventCommand command) {
            if (failNextRecord.compareAndSet(true, false)) {
                throw new IllegalStateException("audit event storage failure");
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
        Long sessionId,
        List<Long> reservationIds,
        int remainingCapacity
    ) {
    }
}
