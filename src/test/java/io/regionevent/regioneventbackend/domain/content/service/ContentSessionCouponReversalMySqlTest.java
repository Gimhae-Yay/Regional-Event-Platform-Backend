package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemption;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRedemptionRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentRepository;
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
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationCancellationUseCase;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(ContentSessionCouponReversalMySqlTest.RefundGatewayConfiguration.class)
class ContentSessionCouponReversalMySqlTest extends NonTransactionalMySqlTestSupport {

    private final CancelContentSessionUseCase cancelContentSessionUseCase;
    private final ReservationCancellationUseCase reservationCancellationUseCase;
    private final PortOnePaymentGateway paymentGateway;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationPriceSnapshotRepository reservationPriceSnapshotRepository;
    private final ReservationRepository reservationRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ContentSessionCouponReversalMySqlTest(
        CancelContentSessionUseCase cancelContentSessionUseCase,
        ReservationCancellationUseCase reservationCancellationUseCase,
        PortOnePaymentGateway paymentGateway,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationPriceSnapshotRepository reservationPriceSnapshotRepository,
        ReservationRepository reservationRepository,
        CouponPolicyRepository couponPolicyRepository,
        CouponRepository couponRepository,
        CouponRedemptionRepository couponRedemptionRepository,
        PaymentRepository paymentRepository,
        RefundRepository refundRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.cancelContentSessionUseCase = cancelContentSessionUseCase;
        this.reservationCancellationUseCase = reservationCancellationUseCase;
        this.paymentGateway = paymentGateway;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationPriceSnapshotRepository = reservationPriceSnapshotRepository;
        this.reservationRepository = reservationRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.couponRepository = couponRepository;
        this.couponRedemptionRepository = couponRedemptionRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @BeforeEach
    void resetPaymentGateway() {
        reset(paymentGateway);
    }

    @Test
    void 사용자0원예약취소_환불응답없이예약출처로쿠폰을복구한다() {
        Fixture fixture = createFixture(false);

        io.regionevent.regioneventbackend.domain.reservation.dto.CancelReservationResponse response =
            reservationCancellationUseCase.cancel(
                fixture.visitorUserId(),
                fixture.reservationId(),
                UUID.randomUUID()
            );

        assertThat(response.refund()).isNull();
        assertThat(couponRepository.findById(fixture.couponId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.AVAILABLE);
        assertReversal(fixture.redemptionId(), null, "RESERVATION_CANCELLED");
    }

    @Test
    void 운영자회차취소_0원예약은환불행없이예약출처로쿠폰을복구한다() {
        Fixture fixture = createFixture(false);

        CancelContentSessionResult result = cancelContentSessionUseCase.cancel(
            fixture.operatorUserId(),
            fixture.sessionId(),
            "폭우로 인한 회차 취소",
            UUID.randomUUID()
        );

        assertThat(result.status()).isEqualTo(ContentSessionStatus.CANCELLED);
        assertThat(refundRepository.count()).isZero();
        assertThat(reservationRepository.findById(fixture.reservationId()).orElseThrow().getStatus())
            .isEqualTo(ReservationStatus.CANCELLED);
        assertThat(couponRepository.findById(fixture.couponId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.AVAILABLE);
        assertReversal(fixture.redemptionId(), null, "RESERVATION_CANCELLED");
    }

    @Test
    void 사용자유료예약취소_최초환불성공은실제환불출처로쿠폰을복구한다() {
        Fixture fixture = createFixture(true);
        when(paymentGateway.cancelPayment(fixture.portonePaymentId(), 9_000L, "예약 취소"))
            .thenReturn(new PortOnePaymentGateway.PortOneCancellation(
                "cancel-user",
                "SUCCEEDED",
                "result-hash"
            ));

        io.regionevent.regioneventbackend.domain.reservation.dto.CancelReservationResponse response =
            reservationCancellationUseCase.cancel(
                fixture.visitorUserId(),
                fixture.reservationId(),
                UUID.randomUUID()
            );

        assertThat(response.refund()).isNotNull();
        io.regionevent.regioneventbackend.domain.payment.entity.Refund refund = refundRepository.findAll()
            .getFirst();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertReversal(fixture.redemptionId(), refund.getRefundId(), "REFUND_SUCCEEDED");
    }

    @Test
    void 운영자회차취소_유료예약은핵심취소커밋후실제환불출처로쿠폰을복구한다() {
        Fixture fixture = createFixture(true);
        when(paymentGateway.cancelPayment(fixture.portonePaymentId(), 9_000L, "예약 취소"))
            .thenReturn(new PortOnePaymentGateway.PortOneCancellation(
                "cancel-session",
                "SUCCEEDED",
                "result-hash"
            ));

        CancelContentSessionResult result = cancelContentSessionUseCase.cancel(
            fixture.operatorUserId(),
            fixture.sessionId(),
            "폭우로 인한 회차 취소",
            UUID.randomUUID()
        );

        assertThat(result.status()).isEqualTo(ContentSessionStatus.CANCELLED);
        io.regionevent.regioneventbackend.domain.payment.entity.Refund refund = refundRepository.findAll()
            .getFirst();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(reservationRepository.findById(fixture.reservationId()).orElseThrow().getStatus())
            .isEqualTo(ReservationStatus.CANCELLED);
        assertThat(couponRepository.findById(fixture.couponId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.AVAILABLE);
        assertReversal(fixture.redemptionId(), refund.getRefundId(), "REFUND_SUCCEEDED");
    }

    @Test
    void 운영자회차취소_환불실패여도완료된회차취소를성공으로반환한다() {
        Fixture fixture = createFixture(true);
        when(paymentGateway.cancelPayment(fixture.portonePaymentId(), 9_000L, "예약 취소"))
            .thenReturn(new PortOnePaymentGateway.PortOneCancellation(
                "cancel-failed",
                "FAILED",
                "result-hash"
            ));

        CancelContentSessionResult result = cancelContentSessionUseCase.cancel(
            fixture.operatorUserId(),
            fixture.sessionId(),
            "폭우로 인한 회차 취소",
            UUID.randomUUID()
        );

        assertThat(result.status()).isEqualTo(ContentSessionStatus.CANCELLED);
        assertThat(refundRepository.findAll().getFirst().getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(couponRepository.findById(fixture.couponId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.USED);
        java.util.Map<String, Object> reversal = jdbcTemplate.queryForMap(
            """
            SELECT status, refund_id, reversal_reason_code, reversed_at
            FROM coupon_redemption
            WHERE coupon_redemption_id = ?
            """,
            fixture.redemptionId()
        );
        assertThat(reversal.get("status")).isEqualTo("CONFIRMED");
        assertThat(reversal.get("refund_id")).isNull();
        assertThat(reversal.get("reversal_reason_code")).isNull();
        assertThat(reversal.get("reversed_at")).isNull();
    }

    private void assertReversal(
        Long redemptionId,
        Long refundId,
        String reasonCode
    ) {
        java.util.Map<String, Object> reversal = jdbcTemplate.queryForMap(
            """
            SELECT refund_id, reversal_reason_code, reversed_at
            FROM coupon_redemption
            WHERE coupon_redemption_id = ?
            """,
            redemptionId
        );
        assertThat(reversal.get("refund_id")).isEqualTo(refundId);
        assertThat(reversal.get("reversal_reason_code")).isEqualTo(reasonCode);
        assertThat(reversal.get("reversed_at")).isNotNull();
    }

    private Fixture createFixture(boolean paid) {
        Instant now = Instant.now();
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("SESSION-" + suffix, "김해시", true));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator-" + suffix + "@example.com",
            "hashed-password",
            "운영자",
            "010-1111-1111",
            AppUserStatus.ACTIVE
        ));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        AppUser visitor = appUserRepository.saveAndFlush(new AppUser(
            "visitor-" + suffix + "@example.com",
            "hashed-password",
            "방문자",
            "010-2222-2222",
            AppUserStatus.ACTIVE
        ));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "회차 취소 쿠폰 테스트",
            "회차 취소 쿠폰 복구를 검증합니다.",
            "김해시",
            "10:00~18:00",
            "055-1234-5678",
            "안내",
            "전체",
            "없음",
            "취소 정책",
            now
        ));
        Instant startsAt = now.plusSeconds(86_400);
        ContentSession session = new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(7_200),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(5_400),
            10
        );
        session.approve(operator, now);
        ContentSession savedSession = contentSessionRepository.saveAndFlush(session);
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            savedSession,
            visitor,
            1,
            CapacityHoldStatus.CONSUMED,
            now.plusSeconds(600),
            now,
            null,
            null
        ));
        Coupon coupon = createUsedCoupon(content, region, visitor, now);
        ReservationPriceSnapshot snapshot = reservationPriceSnapshotRepository.saveAndFlush(
            new ReservationPriceSnapshot(
                hold,
                coupon,
                paid ? 10_000 : 1_000,
                1_000,
                paid ? 9_000 : 0,
                "KRW",
                now
            )
        );
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-" + suffix,
            "qr-" + suffix,
            region,
            hold,
            savedSession,
            visitor,
            ReservationStatus.CONFIRMED,
            now,
            null,
            null,
            null,
            null
        ));
        String portonePaymentId = null;
        if (paid) {
            portonePaymentId = "payment-" + suffix;
            Payment payment = new Payment(hold, snapshot, "order-" + suffix, now);
            payment.approve(reservation, portonePaymentId, now);
            paymentRepository.saveAndFlush(payment);
        }
        CouponRedemption redemption = couponRedemptionRepository.saveAndFlush(new CouponRedemption(
            coupon,
            snapshot,
            reservation,
            now
        ));
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = 9 WHERE session_id = ?",
            savedSession.getSessionId()
        );
        return new Fixture(
            operator.getUserId(),
            visitor.getUserId(),
            savedSession.getSessionId(),
            reservation.getReservationId(),
            coupon.getCouponId(),
            redemption.getCouponRedemptionId(),
            portonePaymentId
        );
    }

    private Coupon createUsedCoupon(
        Content content,
        Region region,
        AppUser visitor,
        Instant now
    ) {
        CouponPolicy policy = new CouponPolicy(
            content,
            region,
            "회차 취소 복구 쿠폰",
            "회차 취소 복구 검증용 쿠폰",
            CouponIssuanceType.VISIT,
            1_000,
            1_000,
            30,
            now.minusSeconds(3_600),
            now.plusSeconds(3_600),
            null
        );
        policy.publish(now);
        Coupon coupon = couponRepository.saveAndFlush(new Coupon(
            couponPolicyRepository.saveAndFlush(policy),
            visitor,
            now.minusSeconds(60),
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

    private record Fixture(
        Long operatorUserId,
        Long visitorUserId,
        Long sessionId,
        Long reservationId,
        Long couponId,
        Long redemptionId,
        String portonePaymentId
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RefundGatewayConfiguration {

        @Bean
        @Primary
        PortOnePaymentGateway paymentGateway() {
            return mock(PortOnePaymentGateway.class);
        }
    }
}
