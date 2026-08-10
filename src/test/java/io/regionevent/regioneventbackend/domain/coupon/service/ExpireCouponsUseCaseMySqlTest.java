package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActorLinkService;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventService;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponStatusHistoryRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest(properties = "coupon.expiration.cron=-")
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ExpireCouponsUseCaseMySqlTest.FailingRecordAuditEventUseCaseConfig.class)
class ExpireCouponsUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private final ExpireCouponsUseCase useCase;
    private final FailingRecordAuditEventUseCase recordAuditEventUseCase;
    private final CouponRepository couponRepository;
    private final CouponStatusHistoryRepository couponStatusHistoryRepository;
    private final AuditEventRepository auditEventRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final AppUserRepository appUserRepository;
    private final RegionRepository regionRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    ExpireCouponsUseCaseMySqlTest(
        ExpireCouponsUseCase useCase,
        FailingRecordAuditEventUseCase recordAuditEventUseCase,
        CouponRepository couponRepository,
        CouponStatusHistoryRepository couponStatusHistoryRepository,
        AuditEventRepository auditEventRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        AppUserRepository appUserRepository,
        RegionRepository regionRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.useCase = useCase;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.couponRepository = couponRepository;
        this.couponStatusHistoryRepository = couponStatusHistoryRepository;
        this.auditEventRepository = auditEventRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.appUserRepository = appUserRepository;
        this.regionRepository = regionRepository;
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @AfterEach
    void resetFailureInjection() {
        recordAuditEventUseCase.resetFailureInjection();
    }

    @Test
    void 만료된_AVAILABLE_쿠폰만_이력과_감사를_남기며_EXPIRED로_전이한다() {
        Fixture fixture = createFixture(2, 1);

        CouponExpirationResult result = useCase.execute();

        assertThat(result.processedBatchCount()).isOne();
        assertThat(result.candidateCouponCount()).isEqualTo(2);
        assertThat(result.expiredCouponCount()).isEqualTo(2);
        assertThat(result.skippedCouponCount()).isZero();
        assertThat(result.failedBatchCount()).isZero();
        assertThat(readCouponStatuses(fixture.expiredCouponIds()))
            .containsOnly(CouponStatus.EXPIRED);
        assertThat(readCouponStatuses(fixture.futureCouponIds()))
            .containsOnly(CouponStatus.AVAILABLE);
        assertThat(couponStatusHistoryRepository.findAll()).hasSize(2).allSatisfy(history -> {
            assertThat(history.getPreviousStatus()).isEqualTo(CouponStatus.AVAILABLE);
            assertThat(history.getNextStatus()).isEqualTo(CouponStatus.EXPIRED);
            assertThat(history.getReasonCode()).isEqualTo("EXPIRATION_SCHEDULE");
            assertThat(history.getActorKind()).isEqualTo("SYSTEM");
        });
        assertThat(auditEventRepository.findAll()).hasSize(2).allSatisfy(event -> {
            assertThat(event.getTargetType()).isEqualTo(AuditEventTargetType.COUPON);
            assertThat(event.getPreviousState()).isEqualTo(CouponStatus.AVAILABLE.name());
            assertThat(event.getNextState()).isEqualTo(CouponStatus.EXPIRED.name());
            assertThat(event.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(event.getReasonCode()).isEqualTo("EXPIRATION_SCHEDULE");
            assertThat(event.getActorKind()).isEqualTo("SYSTEM");
        });

        CouponExpirationResult retryResult = useCase.execute();

        assertThat(retryResult.expiredCouponCount()).isZero();
        assertThat(couponStatusHistoryRepository.count()).isEqualTo(2);
        assertThat(auditEventRepository.count()).isEqualTo(2);
    }

    @Test
    void 백_건_단위로_독립_트랜잭션을_완료한다() {
        createFixture(101, 0);

        CouponExpirationResult result = useCase.execute();

        assertThat(result.processedBatchCount()).isEqualTo(2);
        assertThat(result.candidateCouponCount()).isEqualTo(101);
        assertThat(result.expiredCouponCount()).isEqualTo(101);
        assertThat(couponStatusHistoryRepository.count()).isEqualTo(101);
        assertThat(auditEventRepository.count()).isEqualTo(101);
    }

    @Test
    @Timeout(10)
    void 동시_스케줄러_실행에서도_한_작업만_쿠폰을_만료_처리한다() throws Exception {
        createFixture(1, 0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<CouponExpirationResult> results;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<CouponExpirationResult> first = executorService.submit(() -> executeAfterStart(ready, start));
            Future<CouponExpirationResult> second = executorService.submit(() -> executeAfterStart(ready, start));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }

        assertThat(results.stream().mapToInt(CouponExpirationResult::expiredCouponCount).sum()).isOne();
        assertThat(results.stream().mapToInt(CouponExpirationResult::skippedCouponCount).sum()).isLessThanOrEqualTo(1);
        assertThat(couponStatusHistoryRepository.count()).isOne();
        assertThat(auditEventRepository.count()).isOne();
    }

    @Test
    void 배치_내_감사_기록이_실패하면_모든_상태_이력_감사가_롤백되고_재실행한다() {
        Fixture fixture = createFixture(2, 0);
        recordAuditEventUseCase.failNextRecord();

        CouponExpirationResult failedResult = useCase.execute();

        assertThat(failedResult.failedBatchCount()).isOne();
        assertThat(readCouponStatuses(fixture.expiredCouponIds()))
            .containsOnly(CouponStatus.AVAILABLE);
        assertThat(couponStatusHistoryRepository.count()).isZero();
        assertThat(auditEventRepository.count()).isZero();

        CouponExpirationResult retryResult = useCase.execute();

        assertThat(retryResult.expiredCouponCount()).isEqualTo(2);
        assertThat(readCouponStatuses(fixture.expiredCouponIds()))
            .containsOnly(CouponStatus.EXPIRED);
        assertThat(couponStatusHistoryRepository.count()).isEqualTo(2);
        assertThat(auditEventRepository.count()).isEqualTo(2);
    }

    private CouponExpirationResult executeAfterStart(
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return useCase.execute();
    }

    private Fixture createFixture(
        int expiredCouponCount,
        int futureCouponCount
    ) {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "테스트 지역", true));
            AppUser operator = appUserRepository.saveAndFlush(user("operator-" + suffix + "@example.com"));
            AppUser visitor = appUserRepository.saveAndFlush(user("visitor-" + suffix + "@example.com"));
            Content content = contentRepository.saveAndFlush(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PUBLISHED,
                "테스트 체험",
                "설명",
                "주소",
                "운영 시간",
                "055-000-0000",
                "안내",
                "전체",
                "복장",
                "취소 정책",
                now.minusSeconds(3_600)
            ));
            CouponPolicy couponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
                content,
                region,
                "테스트 쿠폰",
                null,
                CouponIssuanceType.VISIT,
                3_000L,
                10_000L,
                30,
                now.minusSeconds(3_600),
                now.plusSeconds(3_600),
                200L
            ));
            couponPolicy.publish(now.minusSeconds(1));
            couponPolicy = couponPolicyRepository.saveAndFlush(couponPolicy);

            List<Long> expiredCouponIds = createCoupons(couponPolicy, visitor, expiredCouponCount, now.minusSeconds(1));
            List<Long> futureCouponIds = createCoupons(couponPolicy, visitor, futureCouponCount, now.plusSeconds(3_600));
            return new Fixture(expiredCouponIds, futureCouponIds);
        });
    }

    private List<Long> createCoupons(
        CouponPolicy couponPolicy,
        AppUser user,
        int count,
        Instant expiresAt
    ) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(ignored -> couponRepository.saveAndFlush(new Coupon(
                couponPolicy,
                user,
                expiresAt.minusSeconds(60),
                expiresAt
            )).getCouponId())
            .toList();
    }

    private List<CouponStatus> readCouponStatuses(List<Long> couponIds) {
        return transactionTemplate.execute(status -> couponIds.stream()
            .map(couponId -> couponRepository.findById(couponId).orElseThrow().getStatus())
            .toList());
    }

    private AppUser user(String loginIdentifier) {
        return new AppUser(loginIdentifier, "hashed-password", "사용자", "010-1234-5678", AppUserStatus.ACTIVE);
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
    static class FailingRecordAuditEventUseCaseConfig {

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
        public io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent record(AuditEventCommand command) {
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
        List<Long> expiredCouponIds,
        List<Long> futureCouponIds
    ) {
    }
}
