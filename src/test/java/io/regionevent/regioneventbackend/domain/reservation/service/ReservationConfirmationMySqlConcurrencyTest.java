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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

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
import io.regionevent.regioneventbackend.domain.reservation.dto.ReservationConfirmationResponse;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Testcontainers
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class ReservationConfirmationMySqlConcurrencyTest {

    private static final int CONCURRENT_REQUEST_COUNT = 2;
    private static final long CONCURRENT_REQUEST_TIMEOUT_SECONDS = 10;

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    private final ReservationConfirmationService reservationConfirmationService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ReservationConfirmationMySqlConcurrencyTest(
        ReservationConfirmationService reservationConfirmationService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.reservationConfirmationService = reservationConfirmationService;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Test
    void confirm_같은_멱등_키로_동시_요청하면_예약을_한_건만_생성하고_최초_성공_결과를_반환한다() throws Exception {
        ReservationFixtures fixtures = createFixtures();
        ConfirmationRequest request = new ConfirmationRequest("same-key", UUID.randomUUID().toString());

        List<ConfirmationOutcome> outcomes = executeConcurrently(fixtures, List.of(request, request));
        List<ReservationConfirmationResponse> successfulResponses = outcomes.stream()
            .map(ConfirmationOutcome::response)
            .filter(response -> response != null)
            .toList();

        assertThat(outcomes)
            .extracting(ConfirmationOutcome::errorCode)
            .allMatch(errorCode -> errorCode == null || errorCode == ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        assertThat(successfulResponses).isNotEmpty();
        assertThat(countReservationsByHoldId(fixtures.capacityHold().getHoldId())).isEqualTo(1);

        ReservationConfirmationResponse retryResponse = reservationConfirmationService.confirm(
            fixtures.user().getUserId(),
            fixtures.capacityHold().getHoldId(),
            request.idempotencyKey(),
            UUID.randomUUID().toString()
        );

        assertThat(retryResponse).isEqualTo(successfulResponses.getFirst());
    }

    @Test
    void confirm_서로_다른_멱등_키로_같은_홀드를_동시_확정하면_한_요청만_성공한다() throws Exception {
        ReservationFixtures fixtures = createFixtures();
        List<ConfirmationOutcome> outcomes = executeConcurrently(
            fixtures,
            List.of(
                new ConfirmationRequest("first-key", UUID.randomUUID().toString()),
                new ConfirmationRequest("second-key", UUID.randomUUID().toString())
            )
        );

        assertThat(outcomes).extracting(ConfirmationOutcome::errorCode)
            .containsExactlyInAnyOrder(null, ErrorCode.RESERVATION_CONFIRM_CONFLICT);
        assertThat(outcomes).extracting(ConfirmationOutcome::response)
            .filteredOn(response -> response != null)
            .hasSize(1);
        assertThat(countReservationsByHoldId(fixtures.capacityHold().getHoldId())).isEqualTo(1);
        assertThat(capacityHoldRepository.findById(fixtures.capacityHold().getHoldId()).orElseThrow().getStatus())
            .isEqualTo(CapacityHoldStatus.CONSUMED);
    }

    private List<ConfirmationOutcome> executeConcurrently(
        ReservationFixtures fixtures,
        List<ConfirmationRequest> requests
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_REQUEST_COUNT);

        try {
            List<Future<ConfirmationOutcome>> futures = requests.stream()
                .map(request -> executorService.submit(() -> {
                    ready.countDown();
                    start.await(CONCURRENT_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    try {
                        return ConfirmationOutcome.succeeded(
                            reservationConfirmationService.confirm(
                                fixtures.user().getUserId(),
                                fixtures.capacityHold().getHoldId(),
                                request.idempotencyKey(),
                                request.requestId()
                            )
                        );
                    } catch (BusinessException exception) {
                        return ConfirmationOutcome.failed(exception.getErrorCode());
                    }
                }))
                .toList();

            assertThat(ready.await(CONCURRENT_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return futures.stream()
                .map(this::getOutcome)
                .toList();
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(CONCURRENT_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private ConfirmationOutcome getOutcome(Future<ConfirmationOutcome> future) {
        try {
            return future.get(CONCURRENT_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Concurrent reservation confirmation did not complete", exception);
        }
    }

    private long countReservationsByHoldId(Long holdId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM reservation WHERE hold_id = ?",
            Long.class,
            holdId
        );
    }

    private ReservationFixtures createFixtures() {
        String suffix = UUID.randomUUID().toString();
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE" + suffix.substring(0, 4), "김해시", true));
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
            "visitor-" + suffix + "@example.com",
            "hashed-password",
            "예약 사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator-" + suffix + "@example.com",
            "hashed-password",
            "운영자",
            "010-9876-5432",
            AppUserStatus.ACTIVE
        ));
        Instant now = Instant.now();
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 문화 체험",
            "김해 문화를 체험하는 행사입니다.",
            "김해 문화체험 담당",
            "매일 10:00~18:00",
            "055-1234-5678",
            "예약자에게 안내를 제공합니다.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            now.minusSeconds(60)
        ));
        ContentSession contentSession = contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            region,
            now.plusSeconds(3600),
            now.plusSeconds(11400),
            now.plusSeconds(1800),
            now.plusSeconds(10800),
            20
        ));
        CapacityHold capacityHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            contentSession,
            user,
            2,
            CapacityHoldStatus.ACTIVE,
            now.plusSeconds(600),
            null,
            null,
            null
        ));
        return new ReservationFixtures(user, capacityHold);
    }

    private record ConfirmationRequest(String idempotencyKey, String requestId) {
    }

    private record ConfirmationOutcome(
        ReservationConfirmationResponse response,
        ErrorCode errorCode
    ) {

        private static ConfirmationOutcome succeeded(ReservationConfirmationResponse response) {
            return new ConfirmationOutcome(response, null);
        }

        private static ConfirmationOutcome failed(ErrorCode errorCode) {
            return new ConfirmationOutcome(null, errorCode);
        }
    }

    private record ReservationFixtures(AppUser user, CapacityHold capacityHold) {
    }

}
