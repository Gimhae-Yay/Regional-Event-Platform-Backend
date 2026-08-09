package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponIssuanceRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponStatusHistoryRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.stampbook.service.StampbookRewardGrantService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    CouponIssueUseCase.class,
    CouponIssueTransactionService.class,
    CouponIssueDuplicateReadService.class,
    CouponPolicyService.class,
    CouponService.class,
    CouponIssuanceService.class,
    CouponStatusHistoryService.class,
    CouponIssuanceHasher.class,
    VisitService.class,
    StampbookRewardGrantService.class,
    AppUserService.class,
    CouponIssueUseCaseMySqlTest.TestConfig.class
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CouponIssueUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    private final CouponIssueUseCase couponIssueUseCase;
    private final CouponRepository couponRepository;
    private final CouponIssuanceRepository couponIssuanceRepository;
    private final CouponStatusHistoryRepository couponStatusHistoryRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    CouponIssueUseCaseMySqlTest(
        CouponIssueUseCase couponIssueUseCase,
        CouponRepository couponRepository,
        CouponIssuanceRepository couponIssuanceRepository,
        CouponStatusHistoryRepository couponStatusHistoryRepository,
        CouponPolicyRepository couponPolicyRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.couponIssueUseCase = couponIssueUseCase;
        this.couponRepository = couponRepository;
        this.couponIssuanceRepository = couponIssuanceRepository;
        this.couponStatusHistoryRepository = couponStatusHistoryRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.visitRepository = visitRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(10)
    void 같은_방문_쿠폰_동시_요청은_한_건의_발급으로_수렴한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<CouponIssueResult> first = executorService.submit(() -> issueAfterStart(fixture, start));
            Future<CouponIssueResult> second = executorService.submit(() -> issueAfterStart(fixture, start));
            start.countDown();

            CouponIssueResult firstResult = first.get(5, TimeUnit.SECONDS);
            CouponIssueResult secondResult = second.get(5, TimeUnit.SECONDS);

            assertThat(firstResult.duplicate()).isNotEqualTo(secondResult.duplicate());
            assertThat(firstResult.couponId()).isEqualTo(secondResult.couponId());
        }

        assertThat(couponRepository.count()).isOne();
        assertThat(couponIssuanceRepository.count()).isOne();
        assertThat(couponStatusHistoryRepository.count()).isOne();
        assertThat(couponPolicyRepository.findById(fixture.couponPolicy().getCouponPolicyId()))
            .hasValueSatisfying(policy -> assertThat(policy.getIssuedCount()).isOne());
    }

    private CouponIssueResult issueAfterStart(Fixture fixture, CountDownLatch start) {
        await(start);
        return couponIssueUseCase.issue(
            fixture.user().getUserId(),
            fixture.couponPolicy().getCouponPolicyId(),
            new CouponIssueUseCase.CouponIssueCommand(
                CouponIssuanceType.VISIT,
                fixture.visit().getVisitId()
            )
        );
    }

    private Fixture createFixture() {
        return inTransaction(() -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
            AppUser user = appUserRepository.saveAndFlush(user("user-" + suffix + "@example.com"));
            AppUser operator = appUserRepository.saveAndFlush(user("operator-" + suffix + "@example.com"));
            Content content = contentRepository.saveAndFlush(new Content(
                region, operator, ContentType.EVENT_EXPERIENCE, ContentStatus.PUBLISHED,
                "체험", "설명", "주소", "운영시간", "055-000-0000", "안내", "전체", "복장", "정책", NOW
            ));
            ContentSession session = new ContentSession(
                content, region, NOW.plusSeconds(3_600), NOW.plusSeconds(10_800),
                NOW.plusSeconds(1_800), NOW.plusSeconds(9_000), 20
            );
            session.approve(operator, NOW);
            session = contentSessionRepository.saveAndFlush(session);
            CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
                region, session, user, 1, CapacityHoldStatus.CONSUMED, NOW, NOW.plusSeconds(300), null, null
            ));
            Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
                "R-" + suffix, "qr-" + suffix, region, hold, session, user,
                ReservationStatus.CONFIRMED, NOW, null, null, null, null
            ));
            Visit visit = visitRepository.saveAndFlush(new Visit(
                region, reservation, user, content, session, operator, CheckinMethod.QR, NOW
            ));
            CouponPolicy couponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
                content, region, "방문 쿠폰", null, CouponIssuanceType.VISIT,
                3_000L, 10_000L, 30, NOW.minusSeconds(1), NOW.plusSeconds(3_600), 10L
            ));
            couponPolicy.publish(NOW.minusSeconds(1));
            couponPolicyRepository.saveAndFlush(couponPolicy);
            return new Fixture(user, visit, couponPolicy);
        });
    }

    private AppUser user(String loginIdentifier) {
        return new AppUser(loginIdentifier, "hashed-password", "사용자", "010-1234-5678", AppUserStatus.ACTIVE);
    }

    private <T> T inTransaction(TransactionalSupplier<T> supplier) {
        return transactionTemplate.execute(status -> supplier.get());
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent test start timed out");
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

    private record Fixture(AppUser user, Visit visit, CouponPolicy couponPolicy) {
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }
    }
}
