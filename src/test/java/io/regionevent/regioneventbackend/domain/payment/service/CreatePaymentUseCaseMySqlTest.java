package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRedemptionRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponStatusHistoryRepository;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.payment.dto.CreatePaymentRequest;
import io.regionevent.regioneventbackend.domain.payment.dto.CreatePaymentResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotencyStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentIdempotencyRepository;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import tools.jackson.databind.node.JsonNodeFactory;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CreatePaymentUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private final CreatePaymentUseCase createPaymentUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentIdempotencyRepository paymentIdempotencyRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final CouponRepository couponRepository;
    private final CouponStatusHistoryRepository couponStatusHistoryRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final ReservationRepository reservationRepository;
    private final AuditEventRepository auditEventRepository;
    private final CapacityHoldService capacityHoldService;
    private final ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    CreatePaymentUseCaseMySqlTest(
        CreatePaymentUseCase createPaymentUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        PaymentRepository paymentRepository,
        PaymentIdempotencyRepository paymentIdempotencyRepository,
        CouponPolicyRepository couponPolicyRepository,
        CouponRepository couponRepository,
        CouponStatusHistoryRepository couponStatusHistoryRepository,
        CouponRedemptionRepository couponRedemptionRepository,
        ReservationRepository reservationRepository,
        AuditEventRepository auditEventRepository,
        CapacityHoldService capacityHoldService,
        ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.createPaymentUseCase = createPaymentUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.paymentRepository = paymentRepository;
        this.paymentIdempotencyRepository = paymentIdempotencyRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.couponRepository = couponRepository;
        this.couponStatusHistoryRepository = couponStatusHistoryRepository;
        this.couponRedemptionRepository = couponRedemptionRepository;
        this.reservationRepository = reservationRepository;
        this.auditEventRepository = auditEventRepository;
        this.capacityHoldService = capacityHoldService;
        this.expirePendingPaymentForTerminatedHoldUseCase = expirePendingPaymentForTerminatedHoldUseCase;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry, properties -> properties);
    }

    @Test
    void sameKeyReturnsTheSamePendingPaymentWithoutCreatingAnotherPayment() {
        Fixture fixture = createFixture();
        String key = "payment-key-" + System.nanoTime();

        CreatePaymentResponse first = createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            key,
            UUID.randomUUID()
        );
        CreatePaymentResponse retry = createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            key,
            UUID.randomUUID()
        );

        assertThat(first.requiresPayment()).isTrue();
        assertThat(retry.requiresPayment()).isTrue();
        assertThat(retry.payment().paymentId()).isEqualTo(first.payment().paymentId());
        assertThat(paymentRepository.findAll())
            .filteredOn(payment -> payment.getCapacityHold().getHoldId().equals(fixture.hold().getHoldId()))
            .hasSize(1);
        assertThat(paymentIdempotencyRepository.findAll())
            .filteredOn(record -> record.getActorUserId() == fixture.user().getUserId())
            .singleElement()
            .satisfies(record -> assertThat(record.getStatus()).isEqualTo(PaymentIdempotencyStatus.SUCCEEDED));
    }

    @Test
    @Timeout(10)
    void sameKeyConcurrentRequestsConvergeToOnePendingPayment() throws Exception {
        Fixture fixture = createFixture();
        String key = "payment-key-" + System.nanoTime();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<CreatePaymentResponse> first = executorService.submit(
                () -> createAfterStart(fixture, key, ready, start)
            );
            Future<CreatePaymentResponse> second = executorService.submit(
                () -> createAfterStart(fixture, key, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            CreatePaymentResponse firstResponse = first.get(5, TimeUnit.SECONDS);
            CreatePaymentResponse secondResponse = second.get(5, TimeUnit.SECONDS);
            assertThat(firstResponse.payment().paymentId()).isEqualTo(secondResponse.payment().paymentId());
        }

        assertThat(paymentRepository.findAll())
            .filteredOn(payment -> payment.getCapacityHold().getHoldId().equals(fixture.hold().getHoldId()))
            .hasSize(1);
    }

    @Test
    void sameKeyWithDifferentRequestIsRejectedWithoutCreatingAnotherPayment() {
        Fixture fixture = createFixture();
        String key = "payment-key-" + System.nanoTime();
        createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            key,
            UUID.randomUUID()
        );

        assertThatThrownBy(() -> createPaymentUseCase.create(
            fixture.user().getUserId(),
            Long.toString(fixture.hold().getHoldId() + 1),
            new CreatePaymentRequest(null),
            key,
            UUID.randomUUID()
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        assertThat(paymentRepository.findAll()).hasSize(1);
    }

    @Test
    void differentKeysCannotCreateTwoPendingPaymentsForTheSameHold() {
        Fixture fixture = createFixture();
        createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            "payment-key-" + System.nanoTime(),
            UUID.randomUUID()
        );

        assertThatThrownBy(() -> createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            "payment-key-" + System.nanoTime(),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class);
        assertThat(paymentRepository.findAll()).hasSize(1);
    }

    @Test
    void databaseCurrentTimeRejectsAnExpiredHoldBeforeCreatingPaymentArtifacts() {
        Fixture fixture = createFixture();
        jdbcTemplate.update(
            "UPDATE capacity_hold SET expires_at = CURRENT_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE hold_id = ?",
            fixture.hold().getHoldId()
        );

        assertThatThrownBy(() -> createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            "payment-key-" + System.nanoTime(),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class);
        assertThat(paymentRepository.findAll()).isEmpty();
        assertThat(paymentIdempotencyRepository.findAll()).isEmpty();
    }

    @Test
    void zeroAmountPaymentConsumesTheHoldAndRecordsAllStateTransitions() {
        Fixture fixture = createFixture();
        Coupon coupon = createCoupon(fixture, 20_000);
        UUID requestId = UUID.randomUUID();

        CreatePaymentResponse response = createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(JsonNodeFactory.instance.stringNode(coupon.getCouponId().toString())),
            "payment-key-" + System.nanoTime(),
            requestId
        );

        assertThat(response.requiresPayment()).isFalse();
        assertThat(reservationRepository.findAll()).hasSize(1);
        assertThat(paymentRepository.findAll()).isEmpty();
        assertThat(couponRepository.findById(coupon.getCouponId())).get()
            .extracting(Coupon::getStatus)
            .isEqualTo(CouponStatus.USED);
        assertThat(couponStatusHistoryRepository.findAll()).hasSize(2);
        assertThat(couponRedemptionRepository.findAll()).hasSize(1);
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> auditEvent.getRequestId().equals(requestId.toString()))
            .extracting(auditEvent -> auditEvent.getTargetType())
            .containsExactlyInAnyOrder(
                AuditEventTargetType.CAPACITY_HOLD,
                AuditEventTargetType.RESERVATION
            );
    }

    @Test
    void expiredHoldExpiresPendingPaymentAndReleasesReservedCoupon() {
        Fixture fixture = createFixture();
        Coupon coupon = createCoupon(fixture, 1_000);
        createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(JsonNodeFactory.instance.stringNode(coupon.getCouponId().toString())),
            "payment-key-" + System.nanoTime(),
            UUID.randomUUID()
        );
        jdbcTemplate.update(
            "UPDATE capacity_hold SET expires_at = CURRENT_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE hold_id = ?",
            fixture.hold().getHoldId()
        );

        transactionTemplate.executeWithoutResult(status -> capacityHoldService
            .expireOrInvalidateExpiredHoldIfActive(fixture.hold().getHoldId(), "SESSION_STARTED")
            .ifPresent(expirePendingPaymentForTerminatedHoldUseCase::expire));

        assertThat(paymentRepository.findAll()).singleElement()
            .extracting(payment -> payment.getStatus())
            .isEqualTo(PaymentStatus.EXPIRED);
        assertThat(couponRepository.findById(coupon.getCouponId())).get()
            .extracting(Coupon::getStatus)
            .isEqualTo(CouponStatus.AVAILABLE);
        assertThat(couponStatusHistoryRepository.findAll()).hasSize(2);
        assertThat(paymentIdempotencyRepository.findAll()).singleElement()
            .extracting(record -> record.getExpiresAt())
            .isNotNull();
    }

    private CreatePaymentResponse createAfterStart(
        Fixture fixture,
        String key,
        CountDownLatch ready,
        CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(3, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent payment creation did not start");
        }
        return createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.hold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            key,
            UUID.randomUUID()
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
                1
            );
            session.approve(operator, now);
            ContentSession savedSession = contentSessionRepository.saveAndFlush(session);
            contentSessionRepository.decreaseRemainingCapacityIfReservable(
                savedSession.getSessionId(),
                1,
                ContentStatus.PUBLISHED,
                ContentSessionStatus.SCHEDULED
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
            return new Fixture(user, hold, content.getContentId());
        });
    }

    private Coupon createCoupon(Fixture fixture, long discountAmount) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            Content content = contentRepository.findById(fixture.contentId())
                .orElseThrow();
            CouponPolicy couponPolicy = new CouponPolicy(
                content,
                content.getRegion(),
                "Payment coupon",
                null,
                CouponIssuanceType.VISIT,
                discountAmount,
                discountAmount,
                30,
                now.minusSeconds(60),
                now.plusSeconds(3_600),
                null
            );
            couponPolicy.publish(now);
            CouponPolicy savedCouponPolicy = couponPolicyRepository.saveAndFlush(couponPolicy);
            return couponRepository.saveAndFlush(new Coupon(
                savedCouponPolicy,
                appUserRepository.getReferenceById(fixture.user().getUserId()),
                now,
                now.plusSeconds(3_600)
            ));
        });
    }

    private record Fixture(AppUser user, CapacityHold hold, Long contentId) {
    }
}
