package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationHoldRequest;
import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationHoldResponse;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Import({
    CreateReservationHoldUseCaseMySqlTest.FailingCapacityHoldServiceConfig.class,
    CreateReservationHoldUseCaseMySqlTest.FixedApplicationClockConfig.class
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CreateReservationHoldUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private final CreateReservationHoldUseCase createReservationHoldUseCase;
    private final FailingCapacityHoldService failingCapacityHoldService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    private static final Instant APP_CLOCK_INSTANT = Instant.parse("2040-01-01T00:00:00Z");

    @Autowired
    CreateReservationHoldUseCaseMySqlTest(
        CreateReservationHoldUseCase createReservationHoldUseCase,
        FailingCapacityHoldService failingCapacityHoldService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        EntityManager entityManager,
        PlatformTransactionManager transactionManager
    ) {
        this.createReservationHoldUseCase = createReservationHoldUseCase;
        this.failingCapacityHoldService = failingCapacityHoldService;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(
            registry,
            CreateReservationHoldUseCaseMySqlTest::withUseAffectedRows
        );
    }

    @AfterEach
    void resetFailureInjection() {
        failingCapacityHoldService.resetFailureInjection();
    }

    @Test
    @Timeout(10)
    void 정원_1인_회차에_동시_홀드_요청하면_하나만_성공하고_정원과_활성_홀드가_일치한다() throws Exception {
        Fixture fixture = createFixture(1, 2);

        List<HoldCreationResult> results = createConcurrently(fixture, List.of(1, 1));

        assertThat(results).filteredOn(HoldCreationResult::isSuccessful).singleElement();
        assertThat(results)
            .filteredOn(result -> !result.isSuccessful())
            .singleElement()
            .satisfies(result -> assertThat(result.errorCode()).isEqualTo(ErrorCode.RESERVATION_HOLD_CONFLICT));

        CapacityState capacityState = readCapacityStateInNewTransaction(fixture.sessionId());
        assertThat(capacityState.remainingCapacity()).isZero();
        assertThat(capacityState.holds())
            .singleElement()
            .satisfies(hold -> {
                assertThat(hold.status()).isEqualTo(CapacityHoldStatus.ACTIVE);
                assertThat(hold.quantity()).isEqualTo(1);
                assertThat(hold.expiresAt()).isAfter(hold.createdAt());
                assertThat(hold.expiresAt()).isBefore(capacityState.startsAt());
            });
    }

    @Test
    @Timeout(10)
    void 서로_다른_수량의_동시_홀드_요청은_조건부_갱신_성공분만_반영한다() throws Exception {
        Fixture fixture = createFixture(5, 3);

        List<HoldCreationResult> results = createConcurrently(fixture, List.of(3, 2, 2));
        List<Integer> successfulQuantities = results.stream()
            .filter(HoldCreationResult::isSuccessful)
            .map(result -> result.response().quantity())
            .toList();

        assertThat(successfulQuantities).isNotEmpty();
        assertThat(results).filteredOn(result -> !result.isSuccessful())
            .isNotEmpty()
            .allSatisfy(result -> assertThat(result.errorCode()).isEqualTo(ErrorCode.RESERVATION_HOLD_CONFLICT));

        CapacityState capacityState = readCapacityStateInNewTransaction(fixture.sessionId());
        int heldQuantity = capacityState.holds().stream()
            .mapToInt(HoldSnapshot::quantity)
            .sum();

        assertThat(heldQuantity).isEqualTo(successfulQuantities.stream().mapToInt(Integer::intValue).sum());
        assertThat(heldQuantity).isLessThanOrEqualTo(fixture.capacity());
        assertThat(capacityState.remainingCapacity()).isEqualTo(fixture.capacity() - heldQuantity);
        assertThat(capacityState.holds()).allSatisfy(hold -> {
            assertThat(hold.status()).isEqualTo(CapacityHoldStatus.ACTIVE);
            assertThat(hold.expiresAt()).isAfter(hold.createdAt());
            assertThat(hold.expiresAt()).isBefore(capacityState.startsAt());
        });
    }

    @Test
    void 홀드_저장_직후_예외가_발생하면_정원_차감과_홀드_생성이_함께_롤백된다() {
        Fixture fixture = createFixture(2, 1);
        failingCapacityHoldService.failNextCreate();

        assertThatThrownBy(() -> createReservationHoldUseCase.create(
            fixture.userIds().getFirst(),
            new CreateReservationHoldRequest(fixture.sessionId().toString(), 1)
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("capacity hold storage failure");

        CapacityState capacityState = readCapacityStateInNewTransaction(fixture.sessionId());
        assertThat(capacityState.remainingCapacity()).isEqualTo(fixture.capacity());
        assertThat(capacityState.holds()).isEmpty();
    }

    @Test
    void 앱_시계가_MySQL_시계와_달라도_생성된_홀드는_DB_기준으로_확정_가능하다() {
        Fixture fixture = createFixture(1, 1);

        CreateReservationHoldResponse response = createReservationHoldUseCase.create(
            fixture.userIds().getFirst(),
            new CreateReservationHoldRequest(fixture.sessionId().toString(), 1)
        );

        CapacityHold capacityHold = capacityHoldRepository.findById(Long.valueOf(response.holdId())).orElseThrow();
        assertThat(capacityHold.getCreatedAt()).isBefore(APP_CLOCK_INSTANT);
        assertThat(capacityHold.getExpiresAt()).isAfter(readCurrentDatabaseInstant());
        int consumedCount = transactionTemplate.execute(status -> capacityHoldRepository.consumeIfConfirmable(
            capacityHold.getHoldId(),
            fixture.userIds().getFirst()
        ));
        assertThat(consumedCount).isEqualTo(1);
    }

    private List<HoldCreationResult> createConcurrently(
        Fixture fixture,
        List<Integer> quantities
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(quantities.size());
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(quantities.size())) {
            List<Future<HoldCreationResult>> futures = new ArrayList<>();
            for (int index = 0; index < quantities.size(); index++) {
                Long userId = fixture.userIds().get(index);
                int quantity = quantities.get(index);
                futures.add(executorService.submit(() -> createAfterStart(
                    fixture.sessionId(),
                    userId,
                    quantity,
                    ready,
                    start
                )));
            }

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<HoldCreationResult> results = new ArrayList<>();
            for (Future<HoldCreationResult> future : futures) {
                results.add(future.get(5, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private HoldCreationResult createAfterStart(
        Long sessionId,
        Long userId,
        int quantity,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);

        try {
            CreateReservationHoldResponse response = createReservationHoldUseCase.create(
                userId,
                new CreateReservationHoldRequest(sessionId.toString(), quantity)
            );
            return new HoldCreationResult(response, null);
        } catch (BusinessException exception) {
            return new HoldCreationResult(null, exception.getErrorCode());
        }
    }

    private Fixture createFixture(int capacity, int userCount) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.save(new Region("R" + suffix, "김해시", true));
            List<Long> userIds = new ArrayList<>();
            for (int index = 0; index < userCount; index++) {
                AppUser user = appUserRepository.save(new AppUser(
                    "visitor-" + suffix + "-" + index + "@example.com",
                    "hashed-password",
                    "예약 사용자",
                    "010-1234-5678",
                    AppUserStatus.ACTIVE
                ));
                userIds.add(user.getUserId());
            }
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
                capacity
            );
            session.approve(operator, now);
            contentSessionRepository.save(session);
            return new Fixture(session.getSessionId(), capacity, userIds);
        });
    }

    private CapacityState readCapacityStateInNewTransaction(Long sessionId) {
        return transactionTemplate.execute(status -> {
            entityManager.clear();
            ContentSession session = contentSessionRepository.findById(sessionId).orElseThrow();
            List<HoldSnapshot> holds = capacityHoldRepository.findAll().stream()
                .filter(hold -> hold.getContentSession().getSessionId().equals(sessionId))
                .map(hold -> new HoldSnapshot(
                    hold.getQuantity(),
                    hold.getStatus(),
                    hold.getExpiresAt(),
                    hold.getCreatedAt()
                ))
                .toList();
            return new CapacityState(session.getRemainingCapacity(), session.getStartsAt(), holds);
        });
    }

    private Instant readCurrentDatabaseInstant() {
        return transactionTemplate.execute(status -> {
            entityManager.clear();
            BigDecimal epochSeconds = capacityHoldRepository.findCurrentEpochSeconds();
            return Instant.ofEpochSecond(
                epochSeconds.longValue(),
                epochSeconds.remainder(BigDecimal.ONE).movePointRight(9).longValue()
            );
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent hold creation did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent hold creation was interrupted", exception);
        }
    }

    private static String withUseAffectedRows(String jdbcUrl) {
        String parameterPrefix = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + parameterPrefix + "useAffectedRows=true";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingCapacityHoldServiceConfig {

        @Bean
        @Primary
        FailingCapacityHoldService failingCapacityHoldService(CapacityHoldRepository capacityHoldRepository) {
            return new FailingCapacityHoldService(capacityHoldRepository);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedApplicationClockConfig {

        @Bean
        @Primary
        Clock applicationClock() {
            return Clock.fixed(APP_CLOCK_INSTANT, ZoneOffset.UTC);
        }
    }

    static class FailingCapacityHoldService extends CapacityHoldService {

        private final CapacityHoldRepository capacityHoldRepository;
        private final AtomicBoolean failNextCreate = new AtomicBoolean();

        FailingCapacityHoldService(CapacityHoldRepository capacityHoldRepository) {
            super(capacityHoldRepository);
            this.capacityHoldRepository = capacityHoldRepository;
        }

        @Override
        public CapacityHold createActiveHold(
            AppUser user,
            ContentSession contentSession,
            int quantity,
            Instant createdAt,
            Instant expiresAt
        ) {
            CapacityHold capacityHold = super.createActiveHold(
                user,
                contentSession,
                quantity,
                createdAt,
                expiresAt
            );
            if (failNextCreate.compareAndSet(true, false)) {
                capacityHoldRepository.flush();
                throw new IllegalStateException("capacity hold storage failure");
            }
            return capacityHold;
        }

        void failNextCreate() {
            failNextCreate.set(true);
        }

        void resetFailureInjection() {
            failNextCreate.set(false);
        }
    }

    private record Fixture(
        Long sessionId,
        int capacity,
        List<Long> userIds
    ) {
    }

    private record HoldCreationResult(
        CreateReservationHoldResponse response,
        ErrorCode errorCode
    ) {

        private boolean isSuccessful() {
            return response != null;
        }
    }

    private record CapacityState(
        int remainingCapacity,
        Instant startsAt,
        List<HoldSnapshot> holds
    ) {
    }

    private record HoldSnapshot(
        int quantity,
        CapacityHoldStatus status,
        Instant expiresAt,
        Instant createdAt
    ) {
    }
}
