package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.persistence.EntityManager;

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
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@SpringBootTest(properties = {
    "reservation.hold-termination.initial-delay=PT24H",
    "reservation.no-show-completion.initial-delay=PT24H"
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ExpireOrInvalidateCapacityHoldsUseCaseMySqlTest.FailingCapacityHoldServiceConfig.class)
class ExpireOrInvalidateCapacityHoldsUseCaseMySqlTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    private final ExpireOrInvalidateCapacityHoldsUseCase useCase;
    private final FailingCapacityHoldService capacityHoldService;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentRepository contentRepository;
    private final AppUserRepository appUserRepository;
    private final RegionRepository regionRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    ExpireOrInvalidateCapacityHoldsUseCaseMySqlTest(
        ExpireOrInvalidateCapacityHoldsUseCase useCase,
        FailingCapacityHoldService capacityHoldService,
        CapacityHoldRepository capacityHoldRepository,
        ContentSessionRepository contentSessionRepository,
        ContentRepository contentRepository,
        AppUserRepository appUserRepository,
        RegionRepository regionRepository,
        EntityManager entityManager,
        PlatformTransactionManager transactionManager
    ) {
        this.useCase = useCase;
        this.capacityHoldService = capacityHoldService;
        this.capacityHoldRepository = capacityHoldRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.contentRepository = contentRepository;
        this.appUserRepository = appUserRepository;
        this.regionRepository = regionRepository;
        this.entityManager = entityManager;
        transactionTemplate = new TransactionTemplate(transactionManager);
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
        capacityHoldService.resetFailureInjection();
        capacityHoldRepository.deleteAllInBatch();
        contentSessionRepository.deleteAllInBatch();
        contentRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
        regionRepository.deleteAllInBatch();
    }

    @Test
    void 만료된_활성_홀드를_만료처리하고_정원을_한번_복구한다() {
        Fixture fixture = createFixture(
            Instant.now().plusSeconds(3_600),
            Instant.now().minusSeconds(60),
            10,
            3
        );

        HoldTerminationResult result = useCase.execute();
        CapacityState state = readCapacityState(fixture);

        assertThat(result.expiredHoldCount()).isOne();
        assertThat(result.invalidatedHoldCount()).isZero();
        assertThat(result.failedHoldCount()).isZero();
        assertThat(state.status()).isEqualTo(CapacityHoldStatus.EXPIRED);
        assertThat(state.terminalAt()).isNotNull();
        assertThat(state.capacityReleasedAt()).isNotNull();
        assertThat(state.invalidationReason()).isNull();
        assertThat(state.remainingCapacity()).isEqualTo(fixture.capacity());
    }

    @Test
    void 회차가_시작된_활성_홀드는_만료대상과_겹쳐도_무효화한다() {
        Instant startedAt = Instant.now().minusSeconds(60);
        Fixture fixture = createFixture(startedAt, startedAt, 10, 3);

        HoldTerminationResult result = useCase.execute();
        CapacityState state = readCapacityState(fixture);

        assertThat(result.expiredHoldCount()).isZero();
        assertThat(result.invalidatedHoldCount()).isOne();
        assertThat(result.failedHoldCount()).isZero();
        assertThat(state.status()).isEqualTo(CapacityHoldStatus.INVALIDATED);
        assertThat(state.terminalAt()).isNotNull();
        assertThat(state.capacityReleasedAt()).isNotNull();
        assertThat(state.invalidationReason()).isEqualTo("SESSION_STARTED");
        assertThat(state.remainingCapacity()).isEqualTo(fixture.capacity());
    }

    @Test
    void 재실행해도_종결된_홀드의_정원을_다시_복구하지_않는다() {
        Fixture fixture = createFixture(
            Instant.now().plusSeconds(3_600),
            Instant.now().minusSeconds(60),
            10,
            3
        );

        HoldTerminationResult firstResult = useCase.execute();
        HoldTerminationResult retryResult = useCase.execute();
        CapacityState state = readCapacityState(fixture);

        assertThat(firstResult.expiredHoldCount()).isOne();
        assertThat(retryResult.expiredHoldCount()).isZero();
        assertThat(retryResult.invalidatedHoldCount()).isZero();
        assertThat(state.status()).isEqualTo(CapacityHoldStatus.EXPIRED);
        assertThat(state.remainingCapacity()).isEqualTo(fixture.capacity());
    }

    @Test
    void 회차시작_후_만료후보로_처리되어도_무효화한다() {
        Instant startedAt = Instant.now().minusSeconds(60);
        Fixture fixture = createFixture(startedAt, startedAt, 10, 3);
        capacityHoldService.skipNextStartedSessionCandidateLookup();

        HoldTerminationResult result = useCase.execute();
        CapacityState state = readCapacityState(fixture);

        assertThat(result.expiredHoldCount()).isZero();
        assertThat(result.invalidatedHoldCount()).isOne();
        assertThat(result.failedHoldCount()).isZero();
        assertThat(state.status()).isEqualTo(CapacityHoldStatus.INVALIDATED);
        assertThat(state.invalidationReason()).isEqualTo("SESSION_STARTED");
        assertThat(state.remainingCapacity()).isEqualTo(fixture.capacity());
    }

    @Test
    @Timeout(10)
    void 동시_스케줄러_실행에서도_홀드별_정원은_한번만_복구한다() throws Exception {
        Fixture fixture = createFixture(
            Instant.now().plusSeconds(3_600),
            Instant.now().minusSeconds(60),
            10,
            3
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<HoldTerminationResult> results;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<HoldTerminationResult> first = executorService.submit(
                () -> executeAfterStart(ready, start)
            );
            Future<HoldTerminationResult> second = executorService.submit(
                () -> executeAfterStart(ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }

        assertThat(results).extracting(HoldTerminationResult::failedHoldCount).containsOnly(0);
        assertThat(results.stream().mapToInt(HoldTerminationResult::expiredHoldCount).sum()).isOne();
        assertThat(readCapacityState(fixture).remainingCapacity()).isEqualTo(fixture.capacity());
    }

    @Test
    void 후보처리_실패는_상태전이와_정원복구를_함께_롤백하고_다음실행에서_재시도한다() {
        Fixture fixture = createFixture(
            Instant.now().plusSeconds(3_600),
            Instant.now().minusSeconds(60),
            10,
            3
        );
        capacityHoldService.failNextExpiration();

        HoldTerminationResult failedResult = useCase.execute();
        CapacityState failedState = readCapacityState(fixture);

        assertThat(failedResult.expiredHoldCount()).isZero();
        assertThat(failedResult.failedHoldCount()).isOne();
        assertThat(failedState.status()).isEqualTo(CapacityHoldStatus.ACTIVE);
        assertThat(failedState.remainingCapacity()).isEqualTo(fixture.capacity() - fixture.quantity());

        HoldTerminationResult retryResult = useCase.execute();
        CapacityState retryState = readCapacityState(fixture);

        assertThat(retryResult.expiredHoldCount()).isOne();
        assertThat(retryResult.failedHoldCount()).isZero();
        assertThat(retryState.status()).isEqualTo(CapacityHoldStatus.EXPIRED);
        assertThat(retryState.remainingCapacity()).isEqualTo(fixture.capacity());
    }

    @Test
    void 공통서비스는_호출자트랜잭션에_참여하고_독립트랜잭션을_열지_않는다() {
        Fixture fixture = createFixture(
            Instant.now().plusSeconds(3_600),
            Instant.now().minusSeconds(60),
            10,
            3
        );

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(capacityHoldService.expireAndReleaseCapacityIfActive(fixture.holdId())).isTrue();
            status.setRollbackOnly();
        });

        CapacityState rolledBackState = readCapacityState(fixture);
        assertThat(rolledBackState.status()).isEqualTo(CapacityHoldStatus.ACTIVE);
        assertThat(rolledBackState.remainingCapacity()).isEqualTo(fixture.capacity() - fixture.quantity());
        assertThatThrownBy(() -> capacityHoldService.expireAndReleaseCapacityIfActive(fixture.holdId()))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    private HoldTerminationResult executeAfterStart(
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return useCase.execute();
    }

    private Fixture createFixture(
        Instant startsAt,
        Instant expiresAt,
        int capacity,
        int quantity
    ) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
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
                now.minusSeconds(3_600)
            ));
            ContentSession contentSession = new ContentSession(
                content,
                region,
                startsAt,
                startsAt.plusSeconds(7_200),
                startsAt.minusSeconds(1_800),
                startsAt.plusSeconds(3_600),
                capacity
            );
            contentSession.approve(operator, now);
            ContentSession savedSession = contentSessionRepository.save(contentSession);
            CapacityHold capacityHold = capacityHoldRepository.save(new CapacityHold(
                region,
                savedSession,
                visitor,
                quantity,
                CapacityHoldStatus.ACTIVE,
                expiresAt,
                null,
                null,
                null,
                now.minusSeconds(60)
            ));
            savedSession = contentSessionRepository.save(savedSession);
            savedSession = contentSessionRepository.findById(savedSession.getSessionId()).orElseThrow();
            entityManager.flush();
            entityManager.createNativeQuery("""
                UPDATE content_session
                SET remaining_capacity = remaining_capacity - :quantity
                WHERE session_id = :sessionId
                """)
                .setParameter("quantity", quantity)
                .setParameter("sessionId", savedSession.getSessionId())
                .executeUpdate();
            return new Fixture(capacityHold.getHoldId(), savedSession.getSessionId(), capacity, quantity);
        });
    }

    private CapacityState readCapacityState(Fixture fixture) {
        return transactionTemplate.execute(status -> {
            entityManager.clear();
            CapacityHold capacityHold = capacityHoldRepository.findById(fixture.holdId()).orElseThrow();
            ContentSession contentSession = contentSessionRepository.findById(fixture.sessionId()).orElseThrow();
            return new CapacityState(
                capacityHold.getStatus(),
                capacityHold.getTerminalAt(),
                capacityHold.getCapacityReleasedAt(),
                capacityHold.getInvalidationReason(),
                contentSession.getRemainingCapacity()
            );
        });
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
    static class FailingCapacityHoldServiceConfig {

        @Bean
        @Primary
        FailingCapacityHoldService failingCapacityHoldService(
            CapacityHoldRepository capacityHoldRepository
        ) {
            return new FailingCapacityHoldService(capacityHoldRepository);
        }
    }

    static class FailingCapacityHoldService extends CapacityHoldService {

        private final AtomicBoolean failNextExpiration = new AtomicBoolean();
        private final AtomicBoolean skipNextStartedSessionCandidateLookup = new AtomicBoolean();

        FailingCapacityHoldService(CapacityHoldRepository capacityHoldRepository) {
            super(capacityHoldRepository);
        }

        @Override
        @Transactional(propagation = Propagation.MANDATORY)
        public Optional<CapacityHoldStatus> expireOrInvalidateExpiredHoldIfActive(
            Long holdId,
            String invalidationReason
        ) {
            Optional<CapacityHoldStatus> terminatedStatus = super.expireOrInvalidateExpiredHoldIfActive(
                holdId,
                invalidationReason
            );
            if (terminatedStatus.filter(CapacityHoldStatus.EXPIRED::equals).isPresent()
                && failNextExpiration.compareAndSet(true, false)) {
                throw new IllegalStateException("capacity hold termination failure");
            }
            return terminatedStatus;
        }

        @Override
        @Transactional(readOnly = true)
        public List<Long> findActiveHoldIdsForStartedSessions() {
            List<Long> holdIds = super.findActiveHoldIdsForStartedSessions();
            if (skipNextStartedSessionCandidateLookup.compareAndSet(true, false)) {
                return List.of();
            }
            return holdIds;
        }

        void failNextExpiration() {
            failNextExpiration.set(true);
        }

        void skipNextStartedSessionCandidateLookup() {
            skipNextStartedSessionCandidateLookup.set(true);
        }

        void resetFailureInjection() {
            failNextExpiration.set(false);
            skipNextStartedSessionCandidateLookup.set(false);
        }
    }

    private record Fixture(
        Long holdId,
        Long sessionId,
        int capacity,
        int quantity
    ) {
    }

    private record CapacityState(
        CapacityHoldStatus status,
        Instant terminalAt,
        Instant capacityReleasedAt,
        String invalidationReason,
        int remainingCapacity
    ) {
    }
}
