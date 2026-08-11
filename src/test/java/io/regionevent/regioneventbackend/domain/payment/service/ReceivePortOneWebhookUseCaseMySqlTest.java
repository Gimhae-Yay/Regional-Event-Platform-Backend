package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
import io.regionevent.regioneventbackend.domain.payment.dto.CreatePaymentRequest;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentVerification;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
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
    private final PaymentWebhookRepository paymentWebhookRepository;
    private final PaymentVerificationRepository paymentVerificationRepository;
    private final ReservationRepository reservationRepository;
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
        PaymentWebhookRepository paymentWebhookRepository,
        PaymentVerificationRepository paymentVerificationRepository,
        ReservationRepository reservationRepository,
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
        this.paymentWebhookRepository = paymentWebhookRepository;
        this.paymentVerificationRepository = paymentVerificationRepository;
        this.reservationRepository = reservationRepository;
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

    @Test
    @Timeout(10)
    void sameWebhookArrivingConcurrently_changesPaymentDomainStateOnlyOnce() throws Exception {
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
            "PAID"
        ));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<?> first = executorService.submit(() -> receiveAfterStart(ready, start, orderId));
            Future<?> second = executorService.submit(() -> receiveAfterStart(ready, start, orderId));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        verify(paymentGateway, times(2)).findByPaymentId(orderId);
        assertThat(paymentVerificationRepository.findAll())
            .extracting(PaymentVerification::getInternalDecision)
            .containsExactly("APPROVE");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM payment",
            String.class
        )).isEqualTo(PaymentStatus.APPROVED.name());
        assertThat(reservationRepository.findAll()).hasSize(1);
        assertThat(paymentWebhookRepository.findAll())
            .filteredOn(webhook -> WEBHOOK_ID.equals(webhook.getProviderEventId()))
            .hasSize(1);
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
            "DECLINED"
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
            "DECLINED"
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
            Future<?> first = executorService.submit(() -> receiveAfterStart(ready, start, orderId));
            Future<?> second = executorService.submit(() -> receiveAfterStart(ready, start, orderId));
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

    private void receiveAfterStart(
        CountDownLatch ready,
        CountDownLatch start,
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
            WEBHOOK_ID,
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
            return new Fixture(user, hold);
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

    private record Fixture(AppUser user, CapacityHold hold) {
    }
}
