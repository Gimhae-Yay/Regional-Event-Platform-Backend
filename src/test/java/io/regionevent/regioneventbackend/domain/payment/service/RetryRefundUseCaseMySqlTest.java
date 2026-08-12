package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemption;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemptionStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRedemptionRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponStatusHistoryRepository;
import io.regionevent.regioneventbackend.domain.payment.dto.RetryRefundResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptInitiatorKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentRepository;
import io.regionevent.regioneventbackend.domain.payment.repository.RefundAttemptRepository;
import io.regionevent.regioneventbackend.domain.payment.repository.RefundRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationPriceSnapshotRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(RetryRefundUseCaseMySqlTest.RetryTestConfiguration.class)
class RetryRefundUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    private final RetryRefundUseCase useCase;
    private final PortOnePaymentGateway paymentGateway;
    private final RefundRepository refundRepository;
    private final RefundAttemptRepository refundAttemptRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationPriceSnapshotRepository reservationPriceSnapshotRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final CouponStatusHistoryRepository couponStatusHistoryRepository;
    private final AuditEventRepository auditEventRepository;

    @Autowired
    RetryRefundUseCaseMySqlTest(
        RetryRefundUseCase useCase,
        PortOnePaymentGateway paymentGateway,
        RefundRepository refundRepository,
        RefundAttemptRepository refundAttemptRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationPriceSnapshotRepository reservationPriceSnapshotRepository,
        ReservationRepository reservationRepository,
        PaymentRepository paymentRepository,
        CouponPolicyRepository couponPolicyRepository,
        CouponRepository couponRepository,
        CouponRedemptionRepository couponRedemptionRepository,
        CouponStatusHistoryRepository couponStatusHistoryRepository,
        AuditEventRepository auditEventRepository
    ) {
        this.useCase = useCase;
        this.paymentGateway = paymentGateway;
        this.refundRepository = refundRepository;
        this.refundAttemptRepository = refundAttemptRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationPriceSnapshotRepository = reservationPriceSnapshotRepository;
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.couponRepository = couponRepository;
        this.couponRedemptionRepository = couponRedemptionRepository;
        this.couponStatusHistoryRepository = couponStatusHistoryRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(10)
    void retry_동시_요청은_한_번만_외부_호출하고_다른_요청은_상태_충돌로_종료한다() throws Exception {
        RefundFixture fixture = createFailedRefund();
        when(paymentGateway.cancelPayment(fixture.portonePaymentId(), 10_000L, "MANUAL_REFUND_RETRY"))
            .thenReturn(new PortOnePaymentGateway.PortOneCancellation("cancel-2", "SUCCEEDED", "result-hash"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Object> first = executorService.submit(() -> retryAfterStart(fixture, ready, start));
            Future<Object> second = executorService.submit(() -> retryAfterStart(fixture, ready, start));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Object> results = List.of(await(first), await(second));
            assertThat(results).filteredOn(RetryRefundResponse.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(BusinessException.class::isInstance)
                .singleElement()
                .extracting(result -> ((BusinessException) result).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_STATE_CONFLICT);
        }

        assertThat(refundAttemptRepository.findAllByRefundRefundIdOrderByAttemptNoAsc(fixture.refundId()))
            .extracting(RefundAttempt::getAttemptNo)
            .containsExactly(1, 2);
        assertThat(refundRepository.findById(fixture.refundId()).orElseThrow().getStatus())
            .isEqualTo(RefundStatus.SUCCEEDED);
        verify(paymentGateway, times(1)).cancelPayment(
            fixture.portonePaymentId(),
            10_000L,
            "MANUAL_REFUND_RETRY"
        );
    }

    @Test
    void retry_성공하면_연결된_쿠폰_사용을_복구하고_감사_이력을_남긴다() {
        RefundFixture fixture = createFailedRefundWithCoupon();
        UUID requestId = UUID.randomUUID();
        when(paymentGateway.cancelPayment(fixture.portonePaymentId(), 9_000L, "MANUAL_REFUND_RETRY"))
            .thenReturn(new PortOnePaymentGateway.PortOneCancellation("cancel-2", "SUCCEEDED", "result-hash"));

        useCase.retry(fixture.adminUserId(), Long.toString(fixture.refundId()), requestId);

        assertThat(refundRepository.findById(fixture.refundId()).orElseThrow().getStatus())
            .isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(couponRedemptionRepository.findById(fixture.couponRedemptionId()).orElseThrow().getStatus())
            .isEqualTo(CouponRedemptionStatus.REVERSED);
        assertThat(couponRepository.findById(fixture.couponId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.AVAILABLE);
        assertThat(couponStatusHistoryRepository.findAllByCouponCouponIdOrderByOccurredAtAsc(fixture.couponId()))
            .extracting(
                CouponStatusHistory::getPreviousStatus,
                CouponStatusHistory::getNextStatus,
                CouponStatusHistory::getReasonCode
            )
            .containsExactly(tuple(CouponStatus.USED, CouponStatus.AVAILABLE, "REFUND_SUCCEEDED"));
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> requestId.toString().equals(event.getRequestId()))
            .extracting(event -> event.getTargetType())
            .containsExactlyInAnyOrder(AuditEventTargetType.REFUND, AuditEventTargetType.COUPON);
    }

    private Object retryAfterStart(
        RefundFixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            return useCase.retry(fixture.adminUserId(), Long.toString(fixture.refundId()), UUID.randomUUID());
        } catch (BusinessException exception) {
            return exception;
        }
    }

    private Object await(Future<Object> future) throws Exception {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            throw new AssertionError(exception.getCause());
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent retry did not start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for concurrent retry", exception);
        }
    }

    private RefundFixture createFailedRefund() {
        return createFailedRefund(false);
    }

    private RefundFixture createFailedRefundWithCoupon() {
        return createFailedRefund(true);
    }

    private RefundFixture createFailedRefund(boolean withCoupon) {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator-" + System.nanoTime() + "@example.com", "hashed-password", "운영자", "010-1111-1111", AppUserStatus.ACTIVE
        ));
        AppUser visitor = appUserRepository.saveAndFlush(new AppUser(
            "visitor-" + System.nanoTime() + "@example.com", "hashed-password", "방문자", "010-2222-2222", AppUserStatus.ACTIVE
        ));
        AppUser admin = appUserRepository.saveAndFlush(new AppUser(
            "admin-" + System.nanoTime() + "@example.com", "hashed-password", "관리자", "010-3333-3333",
            AppUserAccountKind.PRIVILEGED, AppUserStatus.ACTIVE
        ));
        platformAdminAssignmentRepository.saveAndFlush(new PlatformAdminAssignment(admin, PlatformAdminGrade.PLATFORM_ADMIN));
        Content content = contentRepository.saveAndFlush(new Content(
            region, operator, ContentType.EVENT_EXPERIENCE, ContentStatus.PUBLISHED, "김해 문화 체험",
            "김해 문화를 체험하는 행사입니다.", "김해시 문의", "매일 10:00~18:00", "055-1234-5678",
            "안내 사항을 따라주세요.", "만 7세 이상", "편한 복장", "시작 하루 전까지 취소할 수 있습니다.", NOW
        ));
        ContentSession session = new ContentSession(
            content, region, NOW.plusSeconds(3_600), NOW.plusSeconds(7_200), NOW.plusSeconds(1_800),
            NOW.plusSeconds(5_400), 10
        );
        session.approve(operator, NOW);
        ContentSession savedSession = contentSessionRepository.saveAndFlush(session);
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region, savedSession, visitor, 1, CapacityHoldStatus.CONSUMED, NOW.plusSeconds(600), NOW, null, null
        ));
        Coupon coupon = withCoupon ? createUsedCoupon(content, region, visitor) : null;
        ReservationPriceSnapshot snapshot = reservationPriceSnapshotRepository.saveAndFlush(
            new ReservationPriceSnapshot(
                hold,
                coupon,
                10_000,
                withCoupon ? 1_000 : 0,
                withCoupon ? 9_000 : 10_000,
                "KRW",
                NOW
            )
        );
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-" + System.nanoTime(), "qr-" + System.nanoTime(), region, hold, savedSession, visitor,
            ReservationStatus.CONFIRMED, NOW, null, null, null, null
        ));
        if (withCoupon) {
            reservation.cancel("사용자 취소", NOW.plusSeconds(60), null);
            reservation = reservationRepository.saveAndFlush(reservation);
        }
        String portonePaymentId = "payment-" + System.nanoTime();
        Payment payment = new Payment(hold, snapshot, "order-" + System.nanoTime(), NOW);
        payment.approve(reservation, portonePaymentId, NOW);
        Payment savedPayment = paymentRepository.saveAndFlush(payment);
        Refund refund = new Refund(savedPayment, withCoupon ? 9_000L : 10_000L, NOW);
        refund.startProcessing();
        refund.fail(NOW.plusSeconds(1));
        Refund savedRefund = refundRepository.saveAndFlush(refund);
        refundAttemptRepository.saveAndFlush(new RefundAttempt(
            savedRefund, 1, RefundAttemptInitiatorKind.SYSTEM, NOW
        ));
        CouponRedemption redemption = withCoupon ? couponRedemptionRepository.saveAndFlush(
            new CouponRedemption(coupon, snapshot, reservation, NOW)
        ) : null;
        return new RefundFixture(
            savedRefund.getRefundId(),
            admin.getUserId(),
            portonePaymentId,
            coupon == null ? null : coupon.getCouponId(),
            redemption == null ? null : redemption.getCouponRedemptionId()
        );
    }

    private Coupon createUsedCoupon(
        Content content,
        Region region,
        AppUser visitor
    ) {
        CouponPolicy policy = new CouponPolicy(
            content,
            region,
            "환불 복구 쿠폰",
            "환불 시 복구 검증용 쿠폰",
            CouponIssuanceType.VISIT,
            1_000,
            1_000,
            30,
            NOW.minusSeconds(3_600),
            NOW.plusSeconds(3_600),
            null
        );
        policy.publish(NOW);
        Coupon coupon = couponRepository.saveAndFlush(new Coupon(
            couponPolicyRepository.saveAndFlush(policy),
            visitor,
            NOW.minusSeconds(60),
            NOW.plusSeconds(86_400)
        ));
        coupon.reserve();
        coupon.use();
        return couponRepository.saveAndFlush(coupon);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RetryTestConfiguration {

        @Bean
        @Primary
        PortOnePaymentGateway paymentGateway() {
            return mock(PortOnePaymentGateway.class);
        }
    }

    private record RefundFixture(
        Long refundId,
        Long adminUserId,
        String portonePaymentId,
        Long couponId,
        Long couponRedemptionId
    ) {
    }
}
