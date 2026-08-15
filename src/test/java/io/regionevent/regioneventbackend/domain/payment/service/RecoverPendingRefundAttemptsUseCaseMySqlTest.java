package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

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
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemptionReversalReason;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemptionStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRedemptionRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptInitiatorKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptOutcomeKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundFailureReasonCode;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneLookupException;
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
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(RecoverPendingRefundAttemptsUseCaseMySqlTest.RecoveryTestConfiguration.class)
class RecoverPendingRefundAttemptsUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    private final RecoverPendingRefundAttemptsUseCase useCase;
    private final PortOnePaymentGateway paymentGateway;
    private final RefundRepository refundRepository;
    private final RefundAttemptRepository refundAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final ReservationPriceSnapshotRepository reservationPriceSnapshotRepository;
    private final ReservationRepository reservationRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;

    @Autowired
    RecoverPendingRefundAttemptsUseCaseMySqlTest(
        RecoverPendingRefundAttemptsUseCase useCase,
        PortOnePaymentGateway paymentGateway,
        RefundRepository refundRepository,
        RefundAttemptRepository refundAttemptRepository,
        PaymentRepository paymentRepository,
        ReservationPriceSnapshotRepository reservationPriceSnapshotRepository,
        ReservationRepository reservationRepository,
        CapacityHoldRepository capacityHoldRepository,
        ContentSessionRepository contentSessionRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        CouponPolicyRepository couponPolicyRepository,
        CouponRepository couponRepository,
        CouponRedemptionRepository couponRedemptionRepository
    ) {
        this.useCase = useCase;
        this.paymentGateway = paymentGateway;
        this.refundRepository = refundRepository;
        this.refundAttemptRepository = refundAttemptRepository;
        this.paymentRepository = paymentRepository;
        this.reservationPriceSnapshotRepository = reservationPriceSnapshotRepository;
        this.reservationRepository = reservationRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.couponRepository = couponRepository;
        this.couponRedemptionRepository = couponRedemptionRepository;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void recover_성공_기존_시도만_응답으로_확정하고_새_시도를_만들지_않는다() {
        RefundFixture fixture = createPendingRefundAttempt();
        when(paymentGateway.findByPaymentId(fixture.portonePaymentId())).thenReturn(paymentWithCancellation(
            fixture.portonePaymentId(),
            "SUCCEEDED",
            "DECLINED"
        ));

        useCase.recover();

        RefundAttempt attempt = refundAttemptRepository.findById(fixture.attemptId()).orElseThrow();
        Refund refund = refundRepository.findById(fixture.refundId()).orElseThrow();
        assertThat(attempt.getOutcomeKind()).isEqualTo(RefundAttemptOutcomeKind.RESPONDED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(couponRedemptionRepository.findById(fixture.redemptionId()).orElseThrow())
            .satisfies(redemption -> {
                assertThat(redemption.getStatus()).isEqualTo(CouponRedemptionStatus.REVERSED);
                assertThat(redemption.getRefund().getRefundId()).isEqualTo(fixture.refundId());
                assertThat(redemption.getReversalReasonCode())
                    .isEqualTo(CouponRedemptionReversalReason.REFUND_SUCCEEDED);
                assertThat(redemption.getReversedAt()).isNotNull();
            });
        assertThat(couponRepository.findById(fixture.couponId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.AVAILABLE);
        assertThat(refundAttemptRepository.findAllByRefundRefundIdOrderByAttemptNoAsc(fixture.refundId()))
            .hasSize(1)
            .extracting(RefundAttempt::getAttemptNo)
            .containsExactly(1);
    }

    @Test
    void recover_미처리_기존_시도만_응답으로_확정하고_환불을_실패로_전이한다() {
        RefundFixture fixture = createPendingRefundAttempt();
        when(paymentGateway.findByPaymentId(fixture.portonePaymentId())).thenReturn(new PortOnePaymentGateway.PortOnePayment(
            fixture.portonePaymentId(),
            "transaction-1",
            null,
            10_000L,
            "KRW",
            "PAID",
            "payment-hash"
        ));

        useCase.recover();

        RefundAttempt attempt = refundAttemptRepository.findById(fixture.attemptId()).orElseThrow();
        Refund refund = refundRepository.findById(fixture.refundId()).orElseThrow();
        assertThat(attempt.getOutcomeKind()).isEqualTo(RefundAttemptOutcomeKind.RESPONDED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refundAttemptRepository.findAllByRefundRefundIdOrderByAttemptNoAsc(fixture.refundId())).hasSize(1);
    }

    @Test
    void recover_재조회_실패_기존_시도를_프로세스_중단으로_확정하고_새_시도를_만들지_않는다() {
        RefundFixture fixture = createPendingRefundAttempt();
        when(paymentGateway.findByPaymentId(fixture.portonePaymentId()))
            .thenThrow(new PortOneLookupException(new IllegalStateException("lookup failed")));

        useCase.recover();

        RefundAttempt attempt = refundAttemptRepository.findById(fixture.attemptId()).orElseThrow();
        Refund refund = refundRepository.findById(fixture.refundId()).orElseThrow();
        assertThat(attempt.getOutcomeKind()).isEqualTo(RefundAttemptOutcomeKind.NO_RESPONSE);
        assertThat(attempt.getFailureReasonCode()).isEqualTo(RefundFailureReasonCode.PROCESS_INTERRUPTED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.DISCREPANT);
        assertThat(refundAttemptRepository.findAllByRefundRefundIdOrderByAttemptNoAsc(fixture.refundId())).hasSize(1);
    }

    private RefundFixture createPendingRefundAttempt() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        AppUser visitor = appUserRepository.saveAndFlush(new AppUser(
            "visitor@example.com",
            "hashed-password",
            "방문자",
            "010-9876-5432",
            AppUserStatus.ACTIVE
        ));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 문화 체험",
            "김해 문화를 체험하는 행사입니다.",
            "김해시 문의",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안내 사항을 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            NOW
        ));
        ContentSession session = new ContentSession(
            content,
            region,
            NOW.plusSeconds(3_600),
            NOW.plusSeconds(7_200),
            NOW.plusSeconds(1_800),
            NOW.plusSeconds(5_400),
            10
        );
        session.approve(operator, NOW);
        ContentSession savedSession = contentSessionRepository.saveAndFlush(session);
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            savedSession,
            visitor,
            1,
            CapacityHoldStatus.CONSUMED,
            NOW.plusSeconds(600),
            NOW,
            null,
            null
        ));
        Coupon coupon = createUsedCoupon(content, region, visitor);
        ReservationPriceSnapshot snapshot = reservationPriceSnapshotRepository.saveAndFlush(
            new ReservationPriceSnapshot(hold, coupon, 10_000, 1_000, 9_000, "KRW", NOW)
        );
        Reservation reservation = new Reservation(
            "R-1",
            "qr-1",
            region,
            hold,
            savedSession,
            visitor,
            ReservationStatus.CONFIRMED,
            NOW,
            null,
            null,
            null,
            null
        );
        reservation.cancel("사용자 취소", NOW.plusSeconds(60), null);
        Reservation savedReservation = reservationRepository.saveAndFlush(reservation);
        String portonePaymentId = "payment-" + System.nanoTime();
        Payment payment = new Payment(hold, snapshot, "order-" + System.nanoTime(), NOW);
        payment.approve(savedReservation, portonePaymentId, NOW);
        Payment savedPayment = paymentRepository.saveAndFlush(payment);
        Refund refund = new Refund(savedPayment, 9_000L, NOW.minusSeconds(120));
        refund.startProcessing();
        Refund savedRefund = refundRepository.saveAndFlush(refund);
        RefundAttempt attempt = refundAttemptRepository.saveAndFlush(new RefundAttempt(
            savedRefund,
            1,
            RefundAttemptInitiatorKind.SYSTEM,
            NOW.minusSeconds(120)
        ));
        CouponRedemption redemption = couponRedemptionRepository.saveAndFlush(new CouponRedemption(
            coupon,
            snapshot,
            savedReservation,
            NOW
        ));
        return new RefundFixture(
            savedRefund.getRefundId(),
            attempt.getRefundAttemptId(),
            portonePaymentId,
            coupon.getCouponId(),
            redemption.getCouponRedemptionId()
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
            "복구 쿠폰",
            "1분 복구 검증용 쿠폰",
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
            currentDatabaseTime().plusSeconds(86_400)
        ));
        coupon.reserve();
        coupon.use();
        return couponRepository.saveAndFlush(coupon);
    }

    private Instant currentDatabaseTime() {
        BigDecimal epochSeconds = couponRepository.findCurrentEpochSeconds();
        return Instant.ofEpochSecond(
            epochSeconds.longValue(),
            epochSeconds.remainder(BigDecimal.ONE).movePointRight(9).longValue()
        );
    }

    private PortOnePaymentGateway.PortOnePayment paymentWithCancellation(
        String paymentId,
        String cancellationStatus,
        String paymentStatus
    ) {
        return new PortOnePaymentGateway.PortOnePayment(
            paymentId,
            "transaction-1",
            null,
            9_000L,
            "KRW",
            paymentStatus,
            "payment-hash",
            new PortOnePaymentGateway.PortOneCancellation("cancel-1", cancellationStatus, "result-hash")
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RecoveryTestConfiguration {

        @Bean
        @Primary
        PortOnePaymentGateway paymentGateway() {
            return mock(PortOnePaymentGateway.class);
        }
    }

    private record RefundFixture(
        Long refundId,
        Long attemptId,
        String portonePaymentId,
        Long couponId,
        Long redemptionId
    ) {
    }
}
