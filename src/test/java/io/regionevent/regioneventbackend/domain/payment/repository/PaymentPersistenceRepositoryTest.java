package io.regionevent.regioneventbackend.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.persistence.EntityManager;

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
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancyAction;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotency;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotencyOperation;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentVerification;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentWebhook;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationPriceSnapshotRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class PaymentPersistenceRepositoryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-10T00:00:00Z");

    private final ReservationPriceSnapshotRepository reservationPriceSnapshotRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentIdempotencyRepository paymentIdempotencyRepository;
    private final PaymentVerificationRepository paymentVerificationRepository;
    private final PaymentWebhookRepository paymentWebhookRepository;
    private final PaymentDiscrepancyRepository paymentDiscrepancyRepository;
    private final PaymentDiscrepancyActionRepository paymentDiscrepancyActionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;

    @Autowired
    PaymentPersistenceRepositoryTest(
        ReservationPriceSnapshotRepository reservationPriceSnapshotRepository,
        PaymentRepository paymentRepository,
        PaymentIdempotencyRepository paymentIdempotencyRepository,
        PaymentVerificationRepository paymentVerificationRepository,
        PaymentWebhookRepository paymentWebhookRepository,
        PaymentDiscrepancyRepository paymentDiscrepancyRepository,
        PaymentDiscrepancyActionRepository paymentDiscrepancyActionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ContentSessionRepository contentSessionRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager
    ) {
        this.reservationPriceSnapshotRepository = reservationPriceSnapshotRepository;
        this.paymentRepository = paymentRepository;
        this.paymentIdempotencyRepository = paymentIdempotencyRepository;
        this.paymentVerificationRepository = paymentVerificationRepository;
        this.paymentWebhookRepository = paymentWebhookRepository;
        this.paymentDiscrepancyRepository = paymentDiscrepancyRepository;
        this.paymentDiscrepancyActionRepository = paymentDiscrepancyActionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 가격_스냅샷과_결제_검증_웹훅_불일치_이력을_조회한다() {
        PaymentFixtures fixtures = createPaymentFixtures();
        PaymentVerification verification = paymentVerificationRepository.saveAndFlush(
            new PaymentVerification(
                fixtures.payment(),
                "WEBHOOK",
                10_000,
                "KRW",
                fixtures.payment().getOrderId(),
                "PAID",
                "APPROVE",
                "verification-hash",
                CREATED_AT
            )
        );
        paymentWebhookRepository.saveAndFlush(new PaymentWebhook(
            "provider-event-1",
            fixtures.payment(),
            "AUTHENTICATED",
            "PROCESSED",
            "payload-hash",
            CREATED_AT
        ));
        PaymentDiscrepancy discrepancy = paymentDiscrepancyRepository.saveAndFlush(
            new PaymentDiscrepancy(
                fixtures.payment(),
                "AMOUNT_MISMATCH",
                "OPEN",
                CREATED_AT
            )
        );
        paymentDiscrepancyActionRepository.saveAndFlush(new PaymentDiscrepancyAction(
            discrepancy,
            "MANUAL_REVIEW",
            "audit-event-1",
            "AMOUNT_MISMATCH",
            "PENDING",
            CREATED_AT
        ));
        entityManager.clear();

        assertThat(reservationPriceSnapshotRepository
            .findByCapacityHoldHoldId(fixtures.capacityHold().getHoldId()))
            .map(ReservationPriceSnapshot::getFinalAmount)
            .contains(10_000L);
        assertThat(paymentRepository.findByOrderId("order-1"))
            .map(Payment::getPaymentId)
            .contains(fixtures.payment().getPaymentId());
        assertThat(paymentIdempotencyRepository
            .findByActorUserIdAndOperationAndIdempotencyKeyHashForUpdate(
                fixtures.visitor().getUserId(),
                PaymentIdempotencyOperation.PAYMENT_CREATE,
                "idempotency-key-hash"
            ))
            .isPresent();
        assertThat(paymentVerificationRepository
            .findAllByPaymentPaymentIdOrderByVerifiedAtAsc(fixtures.payment().getPaymentId()))
            .extracting(PaymentVerification::getPaymentVerificationId)
            .containsExactly(verification.getPaymentVerificationId());
        assertThat(paymentWebhookRepository.findByProviderEventId("provider-event-1")).isPresent();
        assertThat(paymentDiscrepancyRepository
            .findByPaymentIdForUpdate(fixtures.payment().getPaymentId()))
            .isPresent();
        assertThat(paymentDiscrepancyActionRepository
            .findAllByPaymentDiscrepancyPaymentDiscrepancyIdOrderByActedAtAsc(
                discrepancy.getPaymentDiscrepancyId()
            ))
            .hasSize(1);
    }

    @Test
    void 같은_홀드에는_진행중인_결제를_하나만_저장한다() {
        PaymentFixtures fixtures = createPaymentFixtures();

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(new Payment(
            fixtures.capacityHold(),
            fixtures.reservationPriceSnapshot(),
            "order-2"
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 가격_스냅샷은_금액_불변식을_요구한다() {
        PaymentFixtures fixtures = createPaymentFixtures();

        assertThatThrownBy(() -> new ReservationPriceSnapshot(
            fixtures.capacityHold(),
            null,
            10_000,
            3_000,
            8_000,
            "KRW",
            CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private PaymentFixtures createPaymentFixtures() {
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
            CapacityHoldStatus.ACTIVE,
            CREATED_AT.plusSeconds(600),
            null,
            null,
            null
        ));
        ReservationPriceSnapshot reservationPriceSnapshot = reservationPriceSnapshotRepository.saveAndFlush(
            new ReservationPriceSnapshot(
                capacityHold,
                null,
                10_000,
                0,
                10_000,
                "KRW",
                CREATED_AT
            )
        );
        Payment payment = paymentRepository.saveAndFlush(new Payment(
            capacityHold,
            reservationPriceSnapshot,
            "order-1"
        ));
        paymentIdempotencyRepository.saveAndFlush(new PaymentIdempotency(
            visitor.getUserId(),
            "idempotency-key-hash",
            "request-hash"
        ));

        return new PaymentFixtures(visitor, capacityHold, reservationPriceSnapshot, payment);
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

    private record PaymentFixtures(
        AppUser visitor,
        CapacityHold capacityHold,
        ReservationPriceSnapshot reservationPriceSnapshot,
        Payment payment
    ) {
    }
}
