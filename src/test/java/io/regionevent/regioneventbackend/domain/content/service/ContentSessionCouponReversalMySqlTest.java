package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
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
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundFailureReasonCode;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.dto.CreateRefundResponse;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneNoResponseException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentRepository;
import io.regionevent.regioneventbackend.domain.payment.repository.RefundRepository;
import io.regionevent.regioneventbackend.domain.payment.service.CreateRefundUseCase;
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
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(ContentSessionCouponReversalMySqlTest.RefundGatewayConfiguration.class)
class ContentSessionCouponReversalMySqlTest extends NonTransactionalMySqlTestSupport {

    private final CancelContentSessionUseCase cancelContentSessionUseCase;
    private final ReservationCancellationUseCase reservationCancellationUseCase;
    private final CreateRefundUseCase createRefundUseCase;
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
        CreateRefundUseCase createRefundUseCase,
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
        this.createRefundUseCase = createRefundUseCase;
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

    @Test
    void 운영자회차취소_혼합다수예약을ID순으로처리하고환불쿠폰홀드정원을일관되게확정한다() {
        Instant databaseTime = currentDatabaseTime();
        SessionFixture session = createSessionFixture(databaseTime.plusSeconds(86_400), 10);
        ReservationFixture succeeded = addReservation(
            session,
            9_000L,
            PaymentStatus.APPROVED,
            databaseTime.plusSeconds(3_600)
        );
        ReservationFixture failed = addReservation(
            session,
            8_000L,
            PaymentStatus.DISCREPANT,
            databaseTime.plusSeconds(3_600)
        );
        ReservationFixture discrepant = addReservation(
            session,
            7_000L,
            PaymentStatus.APPROVED,
            databaseTime.plusSeconds(3_600)
        );
        ReservationFixture free = addReservation(
            session,
            0L,
            null,
            databaseTime.minusSeconds(1)
        );
        PendingHoldFixture pendingHold = addPendingHold(session, 2);
        when(paymentGateway.cancelPayment(succeeded.portonePaymentId(), 9_000L, "예약 취소"))
            .thenReturn(new PortOnePaymentGateway.PortOneCancellation("cancel-success", "SUCCEEDED", "hash-success"));
        when(paymentGateway.cancelPayment(failed.portonePaymentId(), 8_000L, "예약 취소"))
            .thenReturn(new PortOnePaymentGateway.PortOneCancellation("cancel-failed", "FAILED", "hash-failed"));
        when(paymentGateway.cancelPayment(discrepant.portonePaymentId(), 7_000L, "예약 취소"))
            .thenThrow(new PortOneNoResponseException(
                RefundFailureReasonCode.TIMEOUT,
                new RuntimeException("timeout")
            ));

        CancelContentSessionResult result = cancelContentSessionUseCase.cancel(
            session.operatorUserId(),
            session.sessionId(),
            "폭우로 인한 회차 취소",
            UUID.randomUUID()
        );

        assertThat(result.status()).isEqualTo(ContentSessionStatus.CANCELLED);
        InOrder portOneOrder = inOrder(paymentGateway);
        portOneOrder.verify(paymentGateway).cancelPayment(succeeded.portonePaymentId(), 9_000L, "예약 취소");
        portOneOrder.verify(paymentGateway).cancelPayment(failed.portonePaymentId(), 8_000L, "예약 취소");
        portOneOrder.verify(paymentGateway).cancelPayment(discrepant.portonePaymentId(), 7_000L, "예약 취소");
        verify(paymentGateway, times(3)).cancelPayment(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        assertRefund(succeeded, RefundStatus.SUCCEEDED, "RESPONDED", "SUCCEEDED");
        assertRefund(failed, RefundStatus.FAILED, "RESPONDED", "FAILED");
        assertRefund(discrepant, RefundStatus.DISCREPANT, "NO_RESPONSE", null);
        assertThat(refundRepository.count()).isEqualTo(3);
        assertThat(countRefundAttempts()).isEqualTo(3);
        assertThat(countPaymentsByReservation(free.reservationId())).isZero();

        assertThat(couponRepository.findById(succeeded.couponId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.AVAILABLE);
        assertThat(couponRepository.findById(failed.couponId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.USED);
        assertThat(couponRepository.findById(discrepant.couponId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.USED);
        assertThat(couponRepository.findById(free.couponId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.EXPIRED);
        assertReversal(succeeded.redemptionId(), refundId(succeeded.paymentId()), "REFUND_SUCCEEDED");
        assertNoReversal(failed.redemptionId());
        assertNoReversal(discrepant.redemptionId());
        assertReversal(free.redemptionId(), null, "RESERVATION_CANCELLED");
        assertCouponHistory(succeeded.couponId(), "REFUND_SUCCEEDED", "AVAILABLE");
        assertCouponHistory(free.couponId(), "RESERVATION_CANCELLED", "EXPIRED");
        assertAudit("REFUND", refundId(succeeded.paymentId()));
        assertAudit("REFUND", refundId(failed.paymentId()));
        assertAudit("REFUND", refundId(discrepant.paymentId()));
        assertAudit("COUPON", succeeded.couponId());
        assertAudit("COUPON", free.couponId());

        assertCancelledReservation(succeeded.reservationId(), true);
        assertCancelledReservation(failed.reservationId(), true);
        assertCancelledReservation(discrepant.reservationId(), true);
        assertCancelledReservation(free.reservationId(), true);
        assertThat(capacityHoldRepository.findById(pendingHold.holdId()).orElseThrow().getStatus())
            .isEqualTo(CapacityHoldStatus.INVALIDATED);
        assertThat(paymentRepository.findById(pendingHold.paymentId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.EXPIRED);
        assertThat(contentSessionRepository.findById(session.sessionId()).orElseThrow().getRemainingCapacity())
            .isEqualTo(10);
    }

    @Test
    void 운영자회차취소_유료예약의만료쿠폰은최초환불성공후만료상태로복구한다() {
        Instant databaseTime = currentDatabaseTime();
        SessionFixture session = createSessionFixture(databaseTime.plusSeconds(86_400), 10);
        ReservationFixture reservation = addReservation(
            session,
            9_000L,
            PaymentStatus.APPROVED,
            databaseTime.minusSeconds(1)
        );
        when(paymentGateway.cancelPayment(reservation.portonePaymentId(), 9_000L, "예약 취소"))
            .thenReturn(new PortOnePaymentGateway.PortOneCancellation(
                "cancel-expired-coupon",
                "SUCCEEDED",
                "hash-expired-coupon"
            ));

        CancelContentSessionResult result = cancelContentSessionUseCase.cancel(
            session.operatorUserId(),
            session.sessionId(),
            "폭우로 인한 회차 취소",
            UUID.randomUUID()
        );

        assertThat(result.status()).isEqualTo(ContentSessionStatus.CANCELLED);
        assertRefund(reservation, RefundStatus.SUCCEEDED, "RESPONDED", "SUCCEEDED");
        assertThat(couponRepository.findById(reservation.couponId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.EXPIRED);
        assertReversal(
            reservation.redemptionId(),
            refundId(reservation.paymentId()),
            "REFUND_SUCCEEDED"
        );
        assertCouponHistory(reservation.couponId(), "REFUND_SUCCEEDED", "EXPIRED");
        assertAudit("REFUND", refundId(reservation.paymentId()));
        assertAudit("COUPON", reservation.couponId());
    }

    @Test
    void 운영자회차취소_시작후예약은취소하되쿠폰과정원을복구하지않는다() {
        Instant databaseTime = currentDatabaseTime();
        SessionFixture session = createSessionFixture(databaseTime.minusSeconds(60), 10);
        ReservationFixture paid = addReservation(
            session,
            9_000L,
            PaymentStatus.APPROVED,
            databaseTime.plusSeconds(3_600)
        );
        ReservationFixture free = addReservation(
            session,
            0L,
            null,
            databaseTime.plusSeconds(3_600)
        );
        when(paymentGateway.cancelPayment(paid.portonePaymentId(), 9_000L, "예약 취소"))
            .thenReturn(new PortOnePaymentGateway.PortOneCancellation("cancel-after-start", "SUCCEEDED", "hash"));

        CancelContentSessionResult result = cancelContentSessionUseCase.cancel(
            session.operatorUserId(),
            session.sessionId(),
            "현장 사정으로 인한 회차 취소",
            UUID.randomUUID()
        );

        assertThat(result.status()).isEqualTo(ContentSessionStatus.CANCELLED);
        assertRefund(paid, RefundStatus.SUCCEEDED, "RESPONDED", "SUCCEEDED");
        assertThat(couponRepository.findById(paid.couponId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.USED);
        assertThat(couponRepository.findById(free.couponId()).orElseThrow().getStatus())
            .isEqualTo(CouponStatus.USED);
        assertNoReversal(paid.redemptionId());
        assertNoReversal(free.redemptionId());
        assertCancelledReservation(paid.reservationId(), false);
        assertCancelledReservation(free.reservationId(), false);
        assertThat(contentSessionRepository.findById(session.sessionId()).orElseThrow().getRemainingCapacity())
            .isEqualTo(8);
    }

    @Test
    void 운영자회차취소_재요청과기존환불재처리는행감사쿠폰외부호출을추가하지않는다() {
        Instant databaseTime = currentDatabaseTime();
        SessionFixture session = createSessionFixture(databaseTime.plusSeconds(86_400), 10);
        ReservationFixture reservation = addReservation(
            session,
            9_000L,
            PaymentStatus.APPROVED,
            databaseTime.plusSeconds(3_600)
        );
        when(paymentGateway.cancelPayment(reservation.portonePaymentId(), 9_000L, "예약 취소"))
            .thenReturn(new PortOnePaymentGateway.PortOneCancellation("cancel-once", "SUCCEEDED", "hash"));
        cancelContentSessionUseCase.cancel(
            session.operatorUserId(),
            session.sessionId(),
            "폭우로 인한 회차 취소",
            UUID.randomUUID()
        );
        long refundCount = refundRepository.count();
        long attemptCount = countRefundAttempts();
        long couponHistoryCount = countCouponHistory(reservation.couponId());
        long refundAuditCount = countAudit("REFUND", refundId(reservation.paymentId()));
        long couponAuditCount = countAudit("COUPON", reservation.couponId());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> cancelContentSessionUseCase.cancel(
                session.operatorUserId(),
                session.sessionId(),
                "폭우로 인한 회차 취소",
                UUID.randomUUID()
            ))
            .isInstanceOf(BusinessException.class);
        CreateRefundResponse response = createRefundUseCase.createForReservationCancellation(
            reservation.paymentId(),
            null,
            UUID.randomUUID()
        );

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(refundRepository.count()).isEqualTo(refundCount);
        assertThat(countRefundAttempts()).isEqualTo(attemptCount);
        assertThat(countCouponHistory(reservation.couponId())).isEqualTo(couponHistoryCount);
        assertThat(countAudit("REFUND", refundId(reservation.paymentId()))).isEqualTo(refundAuditCount);
        assertThat(countAudit("COUPON", reservation.couponId())).isEqualTo(couponAuditCount);
        verify(paymentGateway, times(1)).cancelPayment(reservation.portonePaymentId(), 9_000L, "예약 취소");
    }

    private void assertReversal(
        Long redemptionId,
        Long refundId,
        String reasonCode
    ) {
        Map<String, Object> reversal = jdbcTemplate.queryForMap(
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

    private void assertNoReversal(Long redemptionId) {
        Map<String, Object> reversal = jdbcTemplate.queryForMap(
            """
            SELECT status, refund_id, reversal_reason_code, reversed_at
            FROM coupon_redemption
            WHERE coupon_redemption_id = ?
            """,
            redemptionId
        );
        assertThat(reversal.get("status")).isEqualTo("CONFIRMED");
        assertThat(reversal.get("refund_id")).isNull();
        assertThat(reversal.get("reversal_reason_code")).isNull();
        assertThat(reversal.get("reversed_at")).isNull();
    }

    private void assertRefund(
        ReservationFixture fixture,
        RefundStatus expectedStatus,
        String expectedOutcomeKind,
        String expectedExternalStatus
    ) {
        Map<String, Object> refund = jdbcTemplate.queryForMap(
            """
            SELECT refund.amount,
                   refund.status,
                   refund_attempt.attempt_no,
                   refund_attempt.outcome_kind,
                   refund_attempt.external_status,
                   refund_attempt.failure_reason_code
            FROM refund
            JOIN refund_attempt ON refund_attempt.refund_id = refund.refund_id
            WHERE refund.payment_id = ?
            """,
            fixture.paymentId()
        );
        assertThat(((Number)refund.get("amount")).longValue()).isEqualTo(fixture.finalAmount());
        assertThat(refund.get("status")).isEqualTo(expectedStatus.name());
        assertThat(((Number)refund.get("attempt_no")).intValue()).isEqualTo(1);
        assertThat(refund.get("outcome_kind")).isEqualTo(expectedOutcomeKind);
        assertThat(refund.get("external_status")).isEqualTo(expectedExternalStatus);
        if (expectedOutcomeKind.equals("NO_RESPONSE")) {
            assertThat(refund.get("failure_reason_code")).isEqualTo("TIMEOUT");
        } else {
            assertThat(refund.get("failure_reason_code")).isNull();
        }
    }

    private void assertCouponHistory(
        Long couponId,
        String reasonCode,
        String nextStatus
    ) {
        Map<String, Object> history = jdbcTemplate.queryForMap(
            """
            SELECT previous_status, next_status, reason_code
            FROM coupon_status_history
            WHERE coupon_id = ?
            """,
            couponId
        );
        assertThat(history.get("previous_status")).isEqualTo("USED");
        assertThat(history.get("next_status")).isEqualTo(nextStatus);
        assertThat(history.get("reason_code")).isEqualTo(reasonCode);
    }

    private void assertAudit(String targetType, Long targetId) {
        assertThat(countAudit(targetType, targetId)).isEqualTo(1L);
    }

    private void assertCancelledReservation(
        Long reservationId,
        boolean capacityReleased
    ) {
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getCapacityReleasedAt() != null).isEqualTo(capacityReleased);
    }

    private Long refundId(Long paymentId) {
        return jdbcTemplate.queryForObject(
            "SELECT refund_id FROM refund WHERE payment_id = ?",
            Long.class,
            paymentId
        );
    }

    private long countRefundAttempts() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refund_attempt", Long.class);
        return count == null ? 0L : count;
    }

    private long countPaymentsByReservation(Long reservationId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM payment WHERE reservation_id = ?",
            Long.class,
            reservationId
        );
        return count == null ? 0L : count;
    }

    private long countCouponHistory(Long couponId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM coupon_status_history WHERE coupon_id = ?",
            Long.class,
            couponId
        );
        return count == null ? 0L : count;
    }

    private long countAudit(String targetType, Long targetId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE target_type = ? AND target_id = ?",
            Long.class,
            targetType,
            targetId
        );
        return count == null ? 0L : count;
    }

    private Fixture createFixture(boolean paid) {
        Instant databaseTime = currentDatabaseTime();
        SessionFixture session = createSessionFixture(databaseTime.plusSeconds(86_400), 10);
        ReservationFixture reservation = addReservation(
            session,
            paid ? 9_000L : 0L,
            paid ? PaymentStatus.APPROVED : null,
            databaseTime.plusSeconds(86_400)
        );
        return new Fixture(
            session.operatorUserId(),
            reservation.visitorUserId(),
            session.sessionId(),
            reservation.reservationId(),
            reservation.couponId(),
            reservation.redemptionId(),
            reservation.portonePaymentId()
        );
    }

    private SessionFixture createSessionFixture(
        Instant startsAt,
        int capacity
    ) {
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
        ContentSession session = new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(7_200),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(5_400),
            capacity
        );
        session.approve(operator, now);
        ContentSession savedSession = contentSessionRepository.saveAndFlush(session);
        return new SessionFixture(
            operator.getUserId(),
            savedSession.getSessionId(),
            region,
            content,
            savedSession
        );
    }

    private ReservationFixture addReservation(
        SessionFixture session,
        long finalAmount,
        PaymentStatus paymentStatus,
        Instant couponExpiresAt
    ) {
        Instant now = Instant.now();
        String suffix = Long.toUnsignedString(System.nanoTime());
        AppUser visitor = createVisitor(suffix);
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            session.region(),
            session.session(),
            visitor,
            1,
            CapacityHoldStatus.CONSUMED,
            now.plusSeconds(600),
            now,
            null,
            null
        ));
        Coupon coupon = createUsedCoupon(
            session.content(),
            session.region(),
            visitor,
            now,
            couponExpiresAt
        );
        ReservationPriceSnapshot snapshot = reservationPriceSnapshotRepository.saveAndFlush(
            new ReservationPriceSnapshot(
                hold,
                coupon,
                finalAmount + 1_000L,
                1_000,
                finalAmount,
                "KRW",
                now
            )
        );
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-" + suffix,
            "qr-" + suffix,
            session.region(),
            hold,
            session.session(),
            visitor,
            ReservationStatus.CONFIRMED,
            now,
            null,
            null,
            null,
            null
        ));
        Long paymentId = null;
        String portonePaymentId = null;
        if (paymentStatus != null) {
            portonePaymentId = "payment-" + suffix;
            Payment payment = new Payment(hold, snapshot, "order-" + suffix, now);
            payment.approve(reservation, portonePaymentId, now);
            Payment savedPayment = paymentRepository.saveAndFlush(payment);
            paymentId = savedPayment.getPaymentId();
            if (paymentStatus == PaymentStatus.DISCREPANT) {
                jdbcTemplate.update(
                    "UPDATE payment SET status = 'DISCREPANT' WHERE payment_id = ?",
                    paymentId
                );
            }
        }
        CouponRedemption redemption = couponRedemptionRepository.saveAndFlush(new CouponRedemption(
            coupon,
            snapshot,
            reservation,
            now
        ));
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = remaining_capacity - 1 WHERE session_id = ?",
            session.sessionId()
        );
        return new ReservationFixture(
            visitor.getUserId(),
            reservation.getReservationId(),
            paymentId,
            finalAmount,
            coupon.getCouponId(),
            redemption.getCouponRedemptionId(),
            portonePaymentId
        );
    }

    private PendingHoldFixture addPendingHold(
        SessionFixture session,
        int quantity
    ) {
        Instant now = Instant.now();
        String suffix = Long.toUnsignedString(System.nanoTime());
        AppUser visitor = createVisitor("pending-" + suffix);
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            session.region(),
            session.session(),
            visitor,
            quantity,
            CapacityHoldStatus.ACTIVE,
            now.plusSeconds(600),
            null,
            null,
            null
        ));
        ReservationPriceSnapshot snapshot = reservationPriceSnapshotRepository.saveAndFlush(
            new ReservationPriceSnapshot(hold, null, 5_000L, 0L, 5_000L, "KRW", now)
        );
        Payment payment = paymentRepository.saveAndFlush(new Payment(
            hold,
            snapshot,
            "order-pending-" + suffix,
            now
        ));
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = remaining_capacity - ? WHERE session_id = ?",
            quantity,
            session.sessionId()
        );
        return new PendingHoldFixture(hold.getHoldId(), payment.getPaymentId());
    }

    private AppUser createVisitor(String suffix) {
        AppUser visitor = appUserRepository.saveAndFlush(new AppUser(
            "visitor-" + suffix + "@example.com",
            "hashed-password",
            "방문자",
            "010-2222-2222",
            AppUserStatus.ACTIVE
        ));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
        return visitor;
    }

    private Coupon createUsedCoupon(
        Content content,
        Region region,
        AppUser visitor,
        Instant now,
        Instant expiresAt
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
            expiresAt
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

    private record SessionFixture(
        Long operatorUserId,
        Long sessionId,
        Region region,
        Content content,
        ContentSession session
    ) {
    }

    private record ReservationFixture(
        Long visitorUserId,
        Long reservationId,
        Long paymentId,
        long finalAmount,
        Long couponId,
        Long redemptionId,
        String portonePaymentId
    ) {
    }

    private record PendingHoldFixture(
        Long holdId,
        Long paymentId
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
