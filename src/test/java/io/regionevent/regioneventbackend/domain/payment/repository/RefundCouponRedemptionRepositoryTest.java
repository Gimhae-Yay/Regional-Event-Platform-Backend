package io.regionevent.regioneventbackend.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

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
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRedemptionRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptInitiatorKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptOutcomeKind;
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

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class RefundCouponRedemptionRepositoryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-10T00:00:00Z");

    private final CouponRedemptionRepository couponRedemptionRepository;
    private final RefundRepository refundRepository;
    private final RefundAttemptRepository refundAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final ReservationPriceSnapshotRepository reservationPriceSnapshotRepository;
    private final ReservationRepository reservationRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final CouponRepository couponRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;

    @Autowired
    RefundCouponRedemptionRepositoryTest(
        CouponRedemptionRepository couponRedemptionRepository,
        RefundRepository refundRepository,
        RefundAttemptRepository refundAttemptRepository,
        PaymentRepository paymentRepository,
        ReservationPriceSnapshotRepository reservationPriceSnapshotRepository,
        ReservationRepository reservationRepository,
        CapacityHoldRepository capacityHoldRepository,
        CouponRepository couponRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentSessionRepository contentSessionRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository
    ) {
        this.couponRedemptionRepository = couponRedemptionRepository;
        this.refundRepository = refundRepository;
        this.refundAttemptRepository = refundAttemptRepository;
        this.paymentRepository = paymentRepository;
        this.reservationPriceSnapshotRepository = reservationPriceSnapshotRepository;
        this.reservationRepository = reservationRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.couponRepository = couponRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
    }

    @Test
    void 쿠폰_사용과_환불_시도는_예약과_결제별로_잠금_조회한다() {
        RefundFixtures fixtures = createRefundFixtures();
        CouponRedemption redemption = couponRedemptionRepository.saveAndFlush(new CouponRedemption(
            fixtures.coupon(),
            fixtures.reservationPriceSnapshot(),
            fixtures.reservation(),
            CREATED_AT
        ));
        Refund refund = refundRepository.saveAndFlush(new Refund(
            fixtures.payment(),
            fixtures.reservationPriceSnapshot().getFinalAmount(),
            CREATED_AT
        ));
        RefundAttempt attempt = refundAttemptRepository.saveAndFlush(new RefundAttempt(
            refund,
            1,
            RefundAttemptInitiatorKind.SYSTEM,
            CREATED_AT
        ));

        assertThat(couponRedemptionRepository
            .findByReservationReservationId(fixtures.reservation().getReservationId()))
            .map(CouponRedemption::getCouponRedemptionId)
            .contains(redemption.getCouponRedemptionId());
        assertThat(refundRepository.findByPaymentIdForUpdate(fixtures.payment().getPaymentId()))
            .map(Refund::getRefundId)
            .contains(refund.getRefundId());
        assertThat(refundAttemptRepository.findRecoveryCandidatesForUpdate(
            RefundAttemptOutcomeKind.PENDING,
            CREATED_AT.plusSeconds(60)
        )).extracting(RefundAttempt::getRefundAttemptId).containsExactly(attempt.getRefundAttemptId());
    }

    @Test
    void 가격_스냅샷당_쿠폰_사용은_하나만_저장된다() {
        RefundFixtures fixtures = createRefundFixtures();
        couponRedemptionRepository.saveAndFlush(new CouponRedemption(
            fixtures.coupon(),
            fixtures.reservationPriceSnapshot(),
            fixtures.reservation(),
            CREATED_AT
        ));

        assertThatThrownBy(() -> couponRedemptionRepository.saveAndFlush(new CouponRedemption(
            fixtures.coupon(),
            fixtures.reservationPriceSnapshot(),
            fixtures.reservation(),
            CREATED_AT.plusSeconds(1)
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 환불_시도_번호는_한번부터_세번까지만_허용한다() {
        RefundFixtures fixtures = createRefundFixtures();
        Refund refund = refundRepository.saveAndFlush(new Refund(
            fixtures.payment(),
            fixtures.reservationPriceSnapshot().getFinalAmount(),
            CREATED_AT
        ));

        assertThatThrownBy(() -> new RefundAttempt(
            refund,
            4,
            RefundAttemptInitiatorKind.SYSTEM,
            CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private RefundFixtures createRefundFixtures() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        AppUser operator = saveUser("operator@example.com", "콘텐츠 운영자");
        AppUser visitor = saveUser("visitor@example.com", "방문자");
        AppUser reviewer = saveUser("reviewer@example.com", "회차 검토자");
        Content content = contentRepository.saveAndFlush(new Content(
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
            CREATED_AT
        ));
        CouponPolicy couponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
            content,
            region,
            "방문 보상",
            "방문 뒤 발급하는 할인 쿠폰입니다.",
            CouponIssuanceType.VISIT,
            3_000,
            10_000,
            30,
            CREATED_AT,
            CREATED_AT.plusSeconds(2_592_000),
            100L
        ));
        Coupon coupon = couponRepository.saveAndFlush(new Coupon(
            couponPolicy,
            visitor,
            CREATED_AT,
            CREATED_AT.plusSeconds(2_592_000)
        ));
        ContentSession contentSession = new ContentSession(
            content,
            region,
            CREATED_AT.plusSeconds(3_600),
            CREATED_AT.plusSeconds(10_800),
            CREATED_AT.plusSeconds(1_800),
            CREATED_AT.plusSeconds(9_000),
            10
        );
        contentSession.approve(reviewer, CREATED_AT);
        ContentSession savedContentSession = contentSessionRepository.saveAndFlush(contentSession);
        CapacityHold capacityHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            savedContentSession,
            visitor,
            1,
            CapacityHoldStatus.CONSUMED,
            CREATED_AT.plusSeconds(600),
            CREATED_AT,
            null,
            null
        ));
        ReservationPriceSnapshot reservationPriceSnapshot = reservationPriceSnapshotRepository.saveAndFlush(
            new ReservationPriceSnapshot(
                capacityHold,
                coupon,
                10_000,
                3_000,
                7_000,
                "KRW",
                CREATED_AT
            )
        );
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-1",
            "qr-1",
            region,
            capacityHold,
            savedContentSession,
            visitor,
            ReservationStatus.CONFIRMED,
            CREATED_AT,
            null,
            null,
            null,
            null
        ));
        Payment payment = paymentRepository.saveAndFlush(new Payment(
            capacityHold,
            reservationPriceSnapshot,
            "order-1"
        ));

        return new RefundFixtures(coupon, reservationPriceSnapshot, reservation, payment);
    }

    private AppUser saveUser(
        String loginIdentifier,
        String name
    ) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            name,
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private record RefundFixtures(
        Coupon coupon,
        ReservationPriceSnapshot reservationPriceSnapshot,
        Reservation reservation,
        Payment payment
    ) {
    }
}
