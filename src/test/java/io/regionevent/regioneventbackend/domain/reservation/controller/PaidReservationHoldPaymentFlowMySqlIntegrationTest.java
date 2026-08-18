package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationPriceSnapshotRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.infra.payment.PortOneWebhookSignatureVerifier;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(PaidReservationHoldPaymentFlowMySqlIntegrationTest.PaymentWebhookTestConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaidReservationHoldPaymentFlowMySqlIntegrationTest extends NonTransactionalMySqlTestSupport {

    private static final int RESERVATION_PRICE = 20_000;
    private static final String WEBHOOK_TIMESTAMP = "1785983465";
    private static final String WEBHOOK_SIGNATURE = "v1,signature";
    private static final String TRANSACTION_ID = "transaction-payment-retry";
    private static final String RESULT_HASH = "provider-response-hash";

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationPriceSnapshotRepository reservationPriceSnapshotRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final CouponRepository couponRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final PortOnePaymentGateway paymentGateway;

    @Autowired
    PaidReservationHoldPaymentFlowMySqlIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        PaymentRepository paymentRepository,
        ReservationRepository reservationRepository,
        ReservationPriceSnapshotRepository reservationPriceSnapshotRepository,
        CouponPolicyRepository couponPolicyRepository,
        CouponRepository couponRepository,
        JwtAccessTokenService jwtAccessTokenService,
        PortOnePaymentGateway paymentGateway
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
        this.reservationPriceSnapshotRepository = reservationPriceSnapshotRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.couponRepository = couponRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.paymentGateway = paymentGateway;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void 유료_콘텐츠_공개_홀드_생성_후_결제를_생성한다() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(post("/api/v1/reservations")
                .header("Authorization", bearerToken(fixture.user()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sessionId": "%d",
                      "quantity": 2
                    }
                    """.formatted(fixture.session().getSessionId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(contentSessionRepository.findById(fixture.session().getSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(1));

        List<CapacityHold> capacityHolds = capacityHoldRepository.findAll();
        assertThat(capacityHolds).singleElement();
        CapacityHold capacityHold = capacityHolds.getFirst();
        Long holdId = capacityHold.getHoldId();
        assertThat(capacityHold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE);
        assertThat(capacityHold.getQuantity()).isEqualTo(2);

        mockMvc.perform(post("/api/v1/me/reservation-holds/{holdId}/payments", holdId)
                .header("Authorization", bearerToken(fixture.user()))
                .header("Idempotency-Key", "paid-hold-payment-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.requiresPayment").value(true))
            .andExpect(jsonPath("$.data.payment.holdId").value(holdId.toString()))
            .andExpect(jsonPath("$.data.payment.status").value("PENDING"))
            .andExpect(jsonPath("$.data.payment.amount.finalAmount").value(RESERVATION_PRICE));

        assertThat(paymentRepository.findAll())
            .singleElement()
            .satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING));
        assertThat(reservationPriceSnapshotRepository.findByCapacityHoldHoldId(holdId))
            .hasValueSatisfying(snapshot -> assertThat(snapshot.getFinalAmount()).isEqualTo(RESERVATION_PRICE));
    }

    @Test
    void 거절된_쿠폰_결제를_새_멱등키로_재시도하면_쿠폰을_다시_선점하고_승인한다() throws Exception {
        Fixture fixture = createFixture();
        Long holdId = createPublicHold(fixture);
        Coupon coupon = createCoupon(fixture);

        createPaymentThroughHttp(fixture.user(), holdId, coupon.getCouponId(), "payment-key-first");
        Payment firstPayment = paymentRepository.findAll().getFirst();
        when(paymentGateway.findByPaymentId(firstPayment.getOrderId())).thenReturn(
            new PortOnePaymentGateway.PortOnePayment(
                firstPayment.getOrderId(),
                TRANSACTION_ID,
                "store-1",
                19_000,
                "KRW",
                "DECLINED",
                RESULT_HASH
            )
        );

        receiveWebhookThroughHttp("webhook-coupon-declined", firstPayment.getOrderId());

        assertThat(couponRepository.findById(coupon.getCouponId()))
            .hasValueSatisfying(savedCoupon -> assertThat(savedCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE));

        createPaymentThroughHttp(fixture.user(), holdId, coupon.getCouponId(), "payment-key-retry");
        Payment retryPayment = paymentRepository.findAll().stream()
            .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
            .findFirst()
            .orElseThrow();
        assertThat(couponRepository.findById(coupon.getCouponId()))
            .hasValueSatisfying(savedCoupon -> assertThat(savedCoupon.getStatus()).isEqualTo(CouponStatus.RESERVED));
        when(paymentGateway.findByPaymentId(retryPayment.getOrderId())).thenReturn(
            new PortOnePaymentGateway.PortOnePayment(
                retryPayment.getOrderId(),
                TRANSACTION_ID,
                "store-1",
                19_000,
                "KRW",
                "PAID",
                RESULT_HASH
            )
        );

        receiveWebhookThroughHttp("webhook-coupon-retry-approved", retryPayment.getOrderId());

        assertThat(paymentRepository.findAll()).extracting(Payment::getStatus)
            .containsExactlyInAnyOrder(PaymentStatus.DECLINED, PaymentStatus.APPROVED);
        assertThat(capacityHoldRepository.findById(holdId))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED));
        assertThat(reservationRepository.findAll()).singleElement();
        assertThat(couponRepository.findById(coupon.getCouponId()))
            .hasValueSatisfying(savedCoupon -> assertThat(savedCoupon.getStatus()).isEqualTo(CouponStatus.USED));
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser user = saveUser("visitor-" + suffix + "@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.VISITOR, null));
        AppUser operator = saveUser("operator-" + suffix + "@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            RESERVATION_PRICE,
            now.minusSeconds(60)
        ));
        ContentSession session = new ContentSession(
            content,
            region,
            now.plusSeconds(3_600),
            now.plusSeconds(10_800),
            now.plusSeconds(1_800),
            now.plusSeconds(9_000),
            3
        );
        session.approve(operator, now);
        return new Fixture(user, contentSessionRepository.saveAndFlush(session));
    }

    private Long createPublicHold(Fixture fixture) throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                .header("Authorization", bearerToken(fixture.user()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sessionId": "%d",
                      "quantity": 1
                    }
                    """.formatted(fixture.session().getSessionId())))
            .andExpect(status().isCreated());
        return capacityHoldRepository.findAll().getFirst().getHoldId();
    }

    private Coupon createCoupon(Fixture fixture) {
        Instant now = Instant.now();
        Content content = contentRepository.findById(fixture.session().getContent().getContentId()).orElseThrow();
        CouponPolicy couponPolicy = new CouponPolicy(
            content,
            content.getRegion(),
            "결제 재시도 쿠폰",
            null,
            CouponIssuanceType.VISIT,
            1_000,
            1_000,
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
    }

    private void createPaymentThroughHttp(AppUser user, Long holdId, Long couponId, String idempotencyKey)
        throws Exception {
        mockMvc.perform(post("/api/v1/me/reservation-holds/{holdId}/payments", holdId)
                .header("Authorization", bearerToken(user))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "couponId": "%d"
                    }
                    """.formatted(couponId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.requiresPayment").value(true))
            .andExpect(jsonPath("$.data.payment.status").value("PENDING"));
    }

    private void receiveWebhookThroughHttp(String webhookId, String orderId) throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/portone")
                .header("webhook-id", webhookId)
                .header("webhook-timestamp", WEBHOOK_TIMESTAMP)
                .header("webhook-signature", WEBHOOK_SIGNATURE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentEvent(orderId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"));
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

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "예약 사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, user.getUserId());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PaymentWebhookTestConfiguration {

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

    private record Fixture(AppUser user, ContentSession session) {
    }
}
