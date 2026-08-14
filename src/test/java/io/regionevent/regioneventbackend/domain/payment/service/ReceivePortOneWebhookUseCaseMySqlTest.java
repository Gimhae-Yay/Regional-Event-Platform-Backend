package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.node.JsonNodeFactory;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRedemptionRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponStatusHistoryRepository;
import io.regionevent.regioneventbackend.domain.payment.dto.CreatePaymentRequest;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentVerification;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotency;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentIdempotencyRepository;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentRepository;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentVerificationRepository;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentWebhookRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.infra.payment.PortOneWebhookSignatureVerifier;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(ReceivePortOneWebhookUseCaseMySqlTest.WebhookTestConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReceivePortOneWebhookUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final String WEBHOOK_ID = "webhook-concurrent";
    private static final String WEBHOOK_TIMESTAMP = "1785983465";
    private static final String WEBHOOK_SIGNATURE = "v1,signature";
    private static final String TRANSACTION_ID = "transaction-concurrent";
    private static final String RESULT_HASH = "provider-response-hash";

    private final ReceivePortOneWebhookUseCase receivePortOneWebhookUseCase;
    private final CreatePaymentUseCase createPaymentUseCase;
    private final PortOnePaymentGateway paymentGateway;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentIdempotencyRepository paymentIdempotencyRepository;
    private final PaymentWebhookRepository paymentWebhookRepository;
    private final PaymentVerificationRepository paymentVerificationRepository;
    private final ReservationRepository reservationRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final CouponRepository couponRepository;
    private final CouponStatusHistoryRepository couponStatusHistoryRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final PaymentIdempotencyService paymentIdempotencyService;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ReceivePortOneWebhookUseCaseMySqlTest(
        ReceivePortOneWebhookUseCase receivePortOneWebhookUseCase,
        CreatePaymentUseCase createPaymentUseCase,
        PortOnePaymentGateway paymentGateway,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        PaymentRepository paymentRepository,
        PaymentIdempotencyRepository paymentIdempotencyRepository,
        PaymentWebhookRepository paymentWebhookRepository,
        PaymentVerificationRepository paymentVerificationRepository,
        ReservationRepository reservationRepository,
        CouponPolicyRepository couponPolicyRepository,
        CouponRepository couponRepository,
        CouponStatusHistoryRepository couponStatusHistoryRepository,
        CouponRedemptionRepository couponRedemptionRepository,
        PaymentIdempotencyService paymentIdempotencyService,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.receivePortOneWebhookUseCase = receivePortOneWebhookUseCase;
        this.createPaymentUseCase = createPaymentUseCase;
        this.paymentGateway = paymentGateway;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.paymentRepository = paymentRepository;
        this.paymentIdempotencyRepository = paymentIdempotencyRepository;
        this.paymentWebhookRepository = paymentWebhookRepository;
        this.paymentVerificationRepository = paymentVerificationRepository;
        this.reservationRepository = reservationRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.couponRepository = couponRepository;
        this.couponStatusHistoryRepository = couponStatusHistoryRepository;
        this.couponRedemptionRepository = couponRedemptionRepository;
        this.paymentIdempotencyService = paymentIdempotencyService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry, properties -> properties);
    }

    @AfterEach
    void clearMocks() {
        org.mockito.Mockito.reset(paymentGateway);
    }

    @ParameterizedTest
    @MethodSource("samePendingWebhookOutcomes")
    @Timeout(10)
    void samePendingWebhookArrivingConcurrently_createsOneVerification(
        String providerStatus,
        long providerAmount,
        String expectedDecision,
        PaymentStatus expectedPaymentStatus
    ) throws Exception {
        Fixture fixture = createFixture();
        createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            "payment-key-" + System.nanoTime(),
            UUID.randomUUID()
        );
        String orderId = paymentRepository.findAll().getFirst().getOrderId();
        when(paymentGateway.findByPaymentId(orderId)).thenReturn(new PortOnePaymentGateway.PortOnePayment(
            orderId,
            TRANSACTION_ID,
            "store-1",
            providerAmount,
            "KRW",
            providerStatus,
            RESULT_HASH
        ));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<?> first = executorService.submit(() -> receiveAfterStart(ready, start, WEBHOOK_ID, orderId));
            Future<?> second = executorService.submit(() -> receiveAfterStart(ready, start, WEBHOOK_ID, orderId));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        verify(paymentGateway, times(2)).findByPaymentId(orderId);
        assertThat(paymentVerificationRepository.findAll()).hasSize(1)
            .extracting(PaymentVerification::getInternalDecision)
            .containsExactly(expectedDecision);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM payment",
            String.class
        )).isEqualTo(expectedPaymentStatus.name());
        if (expectedPaymentStatus == PaymentStatus.APPROVED) {
            assertThat(reservationRepository.findAll()).hasSize(1);
        } else {
            assertThat(reservationRepository.findAll()).isEmpty();
        }
        assertThat(paymentWebhookRepository.findAll())
            .filteredOn(webhook -> WEBHOOK_ID.equals(webhook.getProviderEventId()))
            .hasSize(1);
    }

    @Test
    void paidWebhookWithReservedCoupon_confirmsReservationAndConsumesCouponAtomically() {
        Fixture fixture = createFixture();
        Coupon coupon = createCoupon(fixture);
        createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(JsonNodeFactory.instance.stringNode(coupon.getCouponId().toString())),
            "payment-key-" + System.nanoTime(),
            UUID.randomUUID()
        );
        String orderId = paymentRepository.findAll().getFirst().getOrderId();
        when(paymentGateway.findByPaymentId(orderId)).thenReturn(new PortOnePaymentGateway.PortOnePayment(
            orderId, TRANSACTION_ID, "store-1", 19_000, "KRW", "PAID", RESULT_HASH
        ));

        receivePortOneWebhookUseCase.receive(
            "webhook-coupon-approved", WEBHOOK_TIMESTAMP, WEBHOOK_SIGNATURE, paymentEvent(orderId)
        );

        assertThat(paymentRepository.findAll()).extracting(payment -> payment.getStatus())
            .containsExactly(PaymentStatus.APPROVED);
        assertThat(reservationRepository.findAll()).hasSize(1);
        assertThat(couponRepository.findAll()).extracting(Coupon::getStatus).containsExactly(CouponStatus.USED);
        assertThat(couponStatusHistoryRepository.findAll()).hasSize(2);
        assertThat(couponRedemptionRepository.findAll()).hasSize(1);
    }

    @Test
    @Timeout(10)
    void differentWebhooksArrivingConcurrently_changePaymentDomainStateOnlyOnce() throws Exception {
        Fixture fixture = createFixture();
        createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            "payment-key-" + System.nanoTime(),
            UUID.randomUUID()
        );
        String orderId = paymentRepository.findAll().getFirst().getOrderId();
        when(paymentGateway.findByPaymentId(orderId)).thenReturn(new PortOnePaymentGateway.PortOnePayment(
            orderId,
            TRANSACTION_ID,
            "store-1",
            20_000,
            "KRW",
            "PAID",
            RESULT_HASH
        ));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<?> first = executorService.submit(
                () -> receiveAfterStart(ready, start, "webhook-first", orderId)
            );
            Future<?> second = executorService.submit(
                () -> receiveAfterStart(ready, start, "webhook-second", orderId)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        assertThat(paymentVerificationRepository.findAll())
            .extracting(PaymentVerification::getInternalDecision)
            .containsExactly("APPROVE");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM payment", String.class))
            .isEqualTo(PaymentStatus.APPROVED.name());
        assertThat(reservationRepository.findAll()).hasSize(1);
        assertThat(paymentWebhookRepository.findAll())
            .extracting(webhook -> webhook.getProviderEventId())
            .containsExactlyInAnyOrder("webhook-first", "webhook-second");
    }

    @Test
    void declinedWebhookThenPaidWebhook_preservesDeclinedStateWithoutSecondProviderLookup() {
        Fixture fixture = createFixture();
        createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            "payment-key-" + System.nanoTime(),
            UUID.randomUUID()
        );
        String orderId = paymentRepository.findAll().getFirst().getOrderId();
        when(paymentGateway.findByPaymentId(orderId)).thenReturn(new PortOnePaymentGateway.PortOnePayment(
            orderId,
            TRANSACTION_ID,
            "store-1",
            20_000,
            "KRW",
            "DECLINED",
            RESULT_HASH
        ));

        receivePortOneWebhookUseCase.receive(
            "webhook-declined",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            paymentEvent(orderId)
        );
        receivePortOneWebhookUseCase.receive(
            "webhook-paid-after-decline",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            paymentEvent(orderId)
        );

        verify(paymentGateway, times(1)).findByPaymentId(orderId);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM payment", String.class))
            .isEqualTo(PaymentStatus.DECLINED.name());
        assertThat(reservationRepository.findAll()).isEmpty();
        assertThat(paymentWebhookRepository.findAll()).hasSize(2);
    }

    @Test
    @Timeout(10)
    void sameWebhookArrivingConcurrently_forTerminalPayment_recordsItOnceWithoutProviderLookup() throws Exception {
        Fixture fixture = createFixture();
        createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            "payment-key-" + System.nanoTime(),
            UUID.randomUUID()
        );
        String orderId = paymentRepository.findAll().getFirst().getOrderId();
        when(paymentGateway.findByPaymentId(orderId)).thenReturn(new PortOnePaymentGateway.PortOnePayment(
            orderId,
            TRANSACTION_ID,
            "store-1",
            20_000,
            "KRW",
            "DECLINED",
            RESULT_HASH
        ));
        receivePortOneWebhookUseCase.receive(
            "webhook-declined",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            paymentEvent(orderId)
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<?> first = executorService.submit(() -> receiveAfterStart(ready, start, WEBHOOK_ID, orderId));
            Future<?> second = executorService.submit(() -> receiveAfterStart(ready, start, WEBHOOK_ID, orderId));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        verify(paymentGateway, times(1)).findByPaymentId(orderId);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM payment", String.class))
            .isEqualTo(PaymentStatus.DECLINED.name());
        assertThat(reservationRepository.findAll()).isEmpty();
        assertThat(paymentWebhookRepository.findAll())
            .filteredOn(webhook -> WEBHOOK_ID.equals(webhook.getProviderEventId()))
            .hasSize(1);
    }

    @Test
    void approvedWebhook_setsPaymentIdempotencyExpirationFromFinalizedAt() {
        assertWebhookTerminalPaymentIdempotencyExpiration("PAID", 20_000, PaymentStatus.APPROVED);
    }

    @Test
    void declinedWebhook_setsPaymentIdempotencyExpirationFromFinalizedAt() {
        assertWebhookTerminalPaymentIdempotencyExpiration("DECLINED", 20_000, PaymentStatus.DECLINED);
    }

    @Test
    void discrepantWebhook_setsPaymentIdempotencyExpirationFromFinalizedAt() {
        assertWebhookTerminalPaymentIdempotencyExpiration("PAID", 20_001, PaymentStatus.DISCREPANT);
    }

    @Test
    void alreadyFinalizedPaymentWithNullExpiration_isCorrectedWithoutProviderLookup() {
        Fixture fixture = createFixture();
        createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            "payment-key-" + System.nanoTime(),
            UUID.randomUUID()
        );
        Payment payment = paymentRepository.findAll().getFirst();
        jdbcTemplate.update(
            "UPDATE payment SET status = 'DECLINED', finalized_at = CURRENT_TIMESTAMP(6) WHERE payment_id = ?",
            payment.getPaymentId()
        );

        receivePortOneWebhookUseCase.receive(
            "webhook-existing-terminal",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            paymentEvent(payment.getOrderId())
        );

        Payment finalizedPayment = paymentRepository.findById(payment.getPaymentId()).orElseThrow();
        assertThat(paymentIdempotencyRepository.findAll()).singleElement()
            .extracting(PaymentIdempotency::getExpiresAt)
            .isEqualTo(finalizedPayment.getFinalizedAt().plus(24, ChronoUnit.HOURS));
        verify(paymentGateway, times(0)).findByPaymentId(payment.getOrderId());
    }

    @Test
    void existingWebhookForFinalizedPaymentWithNullExpiration_correctsExpirationWithoutLookupOrDuplicateHistories() {
        Fixture fixture = createFixture();
        createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            "payment-key-" + System.nanoTime(),
            UUID.randomUUID()
        );
        Payment payment = paymentRepository.findAll().getFirst();
        when(paymentGateway.findByPaymentId(payment.getOrderId())).thenReturn(new PortOnePaymentGateway.PortOnePayment(
            payment.getOrderId(),
            TRANSACTION_ID,
            "store-1",
            20_000,
            "KRW",
            "DECLINED",
            RESULT_HASH
        ));
        receivePortOneWebhookUseCase.receive(
            WEBHOOK_ID,
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            paymentEvent(payment.getOrderId())
        );
        int existingWebhookCount = paymentWebhookRepository.findAll().size();
        int existingVerificationCount = paymentVerificationRepository.findAll().size();
        jdbcTemplate.update(
            "UPDATE payment_idempotency SET expires_at = NULL WHERE payment_id = ?",
            payment.getPaymentId()
        );
        org.mockito.Mockito.reset(paymentGateway);

        receivePortOneWebhookUseCase.receive(
            WEBHOOK_ID,
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            paymentEvent(payment.getOrderId())
        );

        Payment finalizedPayment = paymentRepository.findById(payment.getPaymentId()).orElseThrow();
        assertThat(paymentIdempotencyRepository.findAll()).singleElement()
            .extracting(PaymentIdempotency::getExpiresAt)
            .isEqualTo(finalizedPayment.getFinalizedAt().plus(24, ChronoUnit.HOURS));
        verify(paymentGateway, times(0)).findByPaymentId(payment.getOrderId());
        assertThat(paymentWebhookRepository.findAll()).hasSize(existingWebhookCount);
        assertThat(paymentVerificationRepository.findAll()).hasSize(existingVerificationCount);
    }

    @Test
    void cleanupPreservesExpirationAtDatabaseCurrentTimeAndDeletesAfterIt() {
        Fixture fixture = createFixture();
        createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            "payment-key-" + System.nanoTime(),
            UUID.randomUUID()
        );

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET timestamp = UNIX_TIMESTAMP('2030-01-01 00:00:00')");
            try {
                jdbcTemplate.update("""
                    UPDATE payment_idempotency
                    SET expires_at = CURRENT_TIMESTAMP(6)
                    WHERE actor_user_id = ?
                    """, fixture.user().getUserId());

                assertThat(paymentIdempotencyService.deleteExpiredTerminalRecords()).isZero();

                jdbcTemplate.update("""
                    UPDATE payment_idempotency
                    SET expires_at = CURRENT_TIMESTAMP(6) - INTERVAL 1 MICROSECOND
                    WHERE actor_user_id = ?
                    """, fixture.user().getUserId());

                assertThat(paymentIdempotencyService.deleteExpiredTerminalRecords()).isOne();
            } finally {
                jdbcTemplate.execute("SET timestamp = DEFAULT");
            }
        });

        assertThat(paymentIdempotencyRepository.findAll()).isEmpty();
    }

    private void assertWebhookTerminalPaymentIdempotencyExpiration(
        String providerStatus,
        long providerAmount,
        PaymentStatus expectedStatus
    ) {
        Fixture fixture = createFixture();
        createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            "payment-key-" + System.nanoTime(),
            UUID.randomUUID()
        );
        Payment payment = paymentRepository.findAll().getFirst();
        when(paymentGateway.findByPaymentId(payment.getOrderId())).thenReturn(new PortOnePaymentGateway.PortOnePayment(
            payment.getOrderId(),
            TRANSACTION_ID,
            "store-1",
            providerAmount,
            "KRW",
            providerStatus,
            RESULT_HASH
        ));

        receivePortOneWebhookUseCase.receive(
            "webhook-terminal-" + expectedStatus.name(),
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            paymentEvent(payment.getOrderId())
        );

        Payment finalizedPayment = paymentRepository.findAll().getFirst();
        assertThat(finalizedPayment.getStatus()).isEqualTo(expectedStatus);
        assertThat(paymentIdempotencyRepository.findAll()).singleElement()
            .extracting(PaymentIdempotency::getExpiresAt)
            .isEqualTo(finalizedPayment.getFinalizedAt().plus(24, ChronoUnit.HOURS));
    }

    private static Stream<Arguments> samePendingWebhookOutcomes() {
        return Stream.of(
            Arguments.of("PAID", 20_000L, "APPROVE", PaymentStatus.APPROVED),
            Arguments.of("DECLINED", 20_000L, "DECLINE", PaymentStatus.DECLINED),
            Arguments.of("PAID", 20_001L, "DISCREPANT", PaymentStatus.DISCREPANT)
        );
    }

    private void receiveAfterStart(
        CountDownLatch ready,
        CountDownLatch start,
        String webhookId,
        String orderId
    ) {
        ready.countDown();
        try {
            if (!start.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("webhook requests did not start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for webhook start", exception);
        }
        receivePortOneWebhookUseCase.receive(
            webhookId,
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            paymentEvent(orderId)
        );
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.save(new Region("R" + suffix, "Region", true));
            AppUser user = appUserRepository.save(new AppUser(
                "visitor-" + suffix + "@example.com",
                "hashed-password",
                "Visitor",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(user, UserRole.VISITOR, null));
            AppUser operator = appUserRepository.save(new AppUser(
                "operator-" + suffix + "@example.com",
                "hashed-password",
                "Operator",
                "010-9876-5432",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
            Content content = contentRepository.save(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PUBLISHED,
                "Title",
                "Description",
                "Address",
                "Hours",
                "055-123-4567",
                "Guide",
                "Age",
                "Materials",
                "Cancellation policy",
                20_000,
                now
            ));
            ContentSession session = new ContentSession(
                content,
                region,
                now.plusSeconds(3_600),
                now.plusSeconds(10_800),
                now.plusSeconds(1_800),
                now.plusSeconds(9_000),
                2
            );
            session.approve(operator, now);
            ContentSession savedSession = contentSessionRepository.saveAndFlush(session);
            contentSessionRepository.decreaseRemainingCapacityIfReservable(
                savedSession.getSessionId(),
                1,
                ContentStatus.PUBLISHED,
                savedSession.getStatus()
            );
            CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
                region,
                savedSession,
                user,
                1,
                CapacityHoldStatus.ACTIVE,
                now.plusSeconds(600),
                null,
                null,
                null,
                now
            ));
            return new Fixture(user, hold, content);
        });
    }

    private Coupon createCoupon(Fixture fixture) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            Content content = contentRepository.findById(fixture.content().getContentId()).orElseThrow();
            CouponPolicy policy = new CouponPolicy(
                content, content.getRegion(), "Payment coupon", null, CouponIssuanceType.VISIT,
                1_000, 1_000, 30, now.minusSeconds(60), now.plusSeconds(3_600), null
            );
            policy.publish(now);
            CouponPolicy savedPolicy = couponPolicyRepository.saveAndFlush(policy);
            return couponRepository.saveAndFlush(new Coupon(
                savedPolicy, appUserRepository.getReferenceById(fixture.user().getUserId()),
                now, now.plusSeconds(3_600)
            ));
        });
    }

    private String paymentEvent(String orderId) {
        return """
            {
              "type": "Transaction.Paid",
              "timestamp": "2026-08-06T02:31:05Z",
              "data": {
                "storeId": "store-1",
                "paymentId": "%s",
                "transactionId": "%s"
              }
            }
            """.formatted(orderId, TRANSACTION_ID);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class WebhookTestConfiguration {

        @Bean
        @Primary
        PortOneWebhookSignatureVerifier portOneWebhookSignatureVerifier() {
            return mock(PortOneWebhookSignatureVerifier.class);
        }

        @Bean
        @Primary
        PortOnePaymentGateway portOnePaymentGateway() {
            return mock(PortOnePaymentGateway.class);
        }
    }

    private record Fixture(AppUser user, CapacityHold hold, Content content) {
    }
}
