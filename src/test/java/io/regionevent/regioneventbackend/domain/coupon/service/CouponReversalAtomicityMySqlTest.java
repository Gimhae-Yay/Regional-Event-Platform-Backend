package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
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
import io.regionevent.regioneventbackend.domain.payment.dto.ResolveRefundFailureRequest;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentRepository;
import io.regionevent.regioneventbackend.domain.payment.repository.RefundRepository;
import io.regionevent.regioneventbackend.domain.payment.service.ResolveRefundFailureUseCase;
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
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CouponReversalAtomicityMySqlTest extends NonTransactionalMySqlTestSupport {

    private final ResolveRefundFailureUseCase resolveRefundFailureUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationPriceSnapshotRepository reservationPriceSnapshotRepository;
    private final ReservationRepository reservationRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final CouponStatusHistoryRepository couponStatusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final JdbcTemplate jdbcTemplate;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @MockitoSpyBean
    private CouponStatusHistoryService couponStatusHistoryService;

    @Autowired
    CouponReversalAtomicityMySqlTest(
        ResolveRefundFailureUseCase resolveRefundFailureUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationPriceSnapshotRepository reservationPriceSnapshotRepository,
        ReservationRepository reservationRepository,
        CouponPolicyRepository couponPolicyRepository,
        CouponRepository couponRepository,
        CouponRedemptionRepository couponRedemptionRepository,
        CouponStatusHistoryRepository couponStatusHistoryRepository,
        PaymentRepository paymentRepository,
        RefundRepository refundRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.resolveRefundFailureUseCase = resolveRefundFailureUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationPriceSnapshotRepository = reservationPriceSnapshotRepository;
        this.reservationRepository = reservationRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.couponRepository = couponRepository;
        this.couponRedemptionRepository = couponRedemptionRepository;
        this.couponStatusHistoryRepository = couponStatusHistoryRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @BeforeEach
    void resetMocks() {
        reset(
            recordAuditEventUseCase,
            AopTestUtils.<CouponStatusHistoryService>getTargetObject(couponStatusHistoryService)
        );
    }

    @Test
    void 쿠폰감사실패_환불상태와반전쿠폰이력을모두롤백한다() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> resolveSucceeded(fixture)).isInstanceOf(IllegalStateException.class);

        verify(AopTestUtils.<CouponStatusHistoryService>getTargetObject(couponStatusHistoryService))
            .create(any(CouponStatusHistory.class));
        assertUnchanged(fixture);
    }

    @Test
    void 쿠폰상태이력실패_환불상태와반전쿠폰을모두롤백한다() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("history failure"))
            .when(AopTestUtils.<CouponStatusHistoryService>getTargetObject(couponStatusHistoryService))
            .create(any(CouponStatusHistory.class));

        assertThatThrownBy(() -> resolveSucceeded(fixture)).isInstanceOf(IllegalStateException.class);

        assertUnchanged(fixture);
    }

    @Test
    void 다른출처재처리_기존반전기록을보존하고환불성공확정을롤백한다() {
        Fixture fixture = createFixture();
        jdbcTemplate.update(
            """
            UPDATE coupon_redemption
            SET status = 'REVERSED',
                reversal_reason_code = 'RESERVATION_CANCELLED',
                reversed_at = '2026-08-15 01:02:03.123456'
            WHERE coupon_redemption_id = ?
            """,
            fixture.redemptionId()
        );

        assertThatThrownBy(() -> resolveSucceeded(fixture)).isInstanceOf(IllegalStateException.class);

        assertThat(refundRepository.findById(fixture.refundId()).orElseThrow().getStatus())
            .isEqualTo(RefundStatus.DISCREPANT);
        java.util.Map<String, Object> reversal = findReversal(fixture.redemptionId());
        assertThat(reversal.get("refund_id")).isNull();
        assertThat(reversal.get("reversal_reason_code")).isEqualTo("RESERVATION_CANCELLED");
        assertThat(reversal.get("reversed_at").toString()).startsWith("2026-08-15 01:02:03.123456");
        assertThat(couponRepository.findById(fixture.couponId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.USED);
    }

    @Test
    void 동일출처재처리_최초반전기록을유지하는무변경성공이다() {
        Fixture fixture = createFixture();
        jdbcTemplate.update(
            """
            UPDATE coupon_redemption
            SET status = 'REVERSED',
                refund_id = ?,
                reversal_reason_code = 'REFUND_SUCCEEDED',
                reversed_at = '2026-08-15 01:02:03.123456'
            WHERE coupon_redemption_id = ?
            """,
            fixture.refundId(),
            fixture.redemptionId()
        );
        jdbcTemplate.update(
            "UPDATE coupon SET status = 'AVAILABLE' WHERE coupon_id = ?",
            fixture.couponId()
        );

        resolveSucceeded(fixture);

        assertThat(refundRepository.findById(fixture.refundId()).orElseThrow().getStatus())
            .isEqualTo(RefundStatus.SUCCEEDED);
        java.util.Map<String, Object> reversal = findReversal(fixture.redemptionId());
        assertThat(((Number) reversal.get("refund_id")).longValue()).isEqualTo(fixture.refundId());
        assertThat(reversal.get("reversal_reason_code")).isEqualTo("REFUND_SUCCEEDED");
        assertThat(reversal.get("reversed_at").toString()).startsWith("2026-08-15 01:02:03.123456");
        assertThat(couponStatusHistoryRepository.findAllByCouponCouponIdOrderByOccurredAtAsc(fixture.couponId()))
            .isEmpty();
    }

    private void resolveSucceeded(Fixture fixture) {
        resolveRefundFailureUseCase.resolve(
            fixture.adminUserId(),
            fixture.refundId(),
            new ResolveRefundFailureRequest("SUCCEEDED", "PortOne 조회", "실제 성공 확인"),
            UUID.randomUUID()
        );
    }

    private void assertUnchanged(Fixture fixture) {
        assertThat(refundRepository.findById(fixture.refundId()).orElseThrow().getStatus())
            .isEqualTo(RefundStatus.DISCREPANT);
        assertThat(couponRedemptionRepository.findById(fixture.redemptionId()).orElseThrow())
            .satisfies(redemption -> {
                assertThat(redemption.getStatus()).isEqualTo(CouponRedemptionStatus.CONFIRMED);
                assertThat(redemption.getRefund()).isNull();
                assertThat(redemption.getReversalReasonCode()).isNull();
                assertThat(redemption.getReversedAt()).isNull();
            });
        assertThat(couponRepository.findById(fixture.couponId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.USED);
        assertThat(couponStatusHistoryRepository.findAllByCouponCouponIdOrderByOccurredAtAsc(fixture.couponId()))
            .isEmpty();
    }

    private java.util.Map<String, Object> findReversal(Long redemptionId) {
        return jdbcTemplate.queryForMap(
            """
            SELECT refund_id, reversal_reason_code, reversed_at
            FROM coupon_redemption
            WHERE coupon_redemption_id = ?
            """,
            redemptionId
        );
    }

    private Fixture createFixture() {
        Instant now = Instant.now();
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("ATOMIC-" + suffix, "김해시", true));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator-" + suffix + "@example.com",
            "hashed-password",
            "운영자",
            "010-1111-1111",
            AppUserStatus.ACTIVE
        ));
        AppUser visitor = appUserRepository.saveAndFlush(new AppUser(
            "visitor-" + suffix + "@example.com",
            "hashed-password",
            "방문자",
            "010-2222-2222",
            AppUserStatus.ACTIVE
        ));
        AppUser admin = appUserRepository.saveAndFlush(new AppUser(
            "admin-" + suffix + "@example.com",
            "hashed-password",
            "관리자",
            "010-3333-3333",
            AppUserAccountKind.PRIVILEGED,
            AppUserStatus.ACTIVE
        ));
        platformAdminAssignmentRepository.saveAndFlush(new PlatformAdminAssignment(
            admin,
            PlatformAdminGrade.PLATFORM_ADMIN
        ));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "원자성 테스트",
            "쿠폰 반전 원자성을 검증합니다.",
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
            new ReservationPriceSnapshot(hold, coupon, 10_000, 1_000, 9_000, "KRW", now)
        );
        Reservation reservation = new Reservation(
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
        );
        reservation.cancel("사용자 취소", now.plusSeconds(60), null);
        Reservation savedReservation = reservationRepository.saveAndFlush(reservation);
        Payment payment = new Payment(hold, snapshot, "order-" + suffix, now);
        payment.approve(savedReservation, "payment-" + suffix, now);
        Payment savedPayment = paymentRepository.saveAndFlush(payment);
        Refund refund = new Refund(savedPayment, 9_000, now);
        refund.startProcessing();
        refund.markDiscrepant(now.plusSeconds(120));
        Refund savedRefund = refundRepository.saveAndFlush(refund);
        CouponRedemption redemption = couponRedemptionRepository.saveAndFlush(new CouponRedemption(
            coupon,
            snapshot,
            savedReservation,
            now
        ));
        return new Fixture(
            admin.getUserId(),
            savedRefund.getRefundId(),
            coupon.getCouponId(),
            redemption.getCouponRedemptionId()
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
            "원자성 복구 쿠폰",
            "원자성 검증용 쿠폰",
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
        Long adminUserId,
        Long refundId,
        Long couponId,
        Long redemptionId
    ) {
    }
}
