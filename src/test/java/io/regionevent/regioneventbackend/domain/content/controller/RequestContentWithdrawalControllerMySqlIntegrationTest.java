package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentWithdrawalRequestRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentRepository;
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
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RequestContentWithdrawalControllerMySqlIntegrationTest extends NonTransactionalMySqlTestSupport {

    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final long RESERVATION_PRICE = 5_000L;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationPriceSnapshotRepository reservationPriceSnapshotRepository;
    private final PaymentRepository paymentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final CouponRepository couponRepository;
    private final ContentWithdrawalRequestRepository withdrawalRequestRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    RequestContentWithdrawalControllerMySqlIntegrationTest(
        MockMvc mockMvc,
        ObjectMapper objectMapper,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentRevisionRepository contentRevisionRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        ReservationPriceSnapshotRepository reservationPriceSnapshotRepository,
        PaymentRepository paymentRepository,
        CouponPolicyRepository couponPolicyRepository,
        CouponRepository couponRepository,
        ContentWithdrawalRequestRepository withdrawalRequestRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        JwtAccessTokenService jwtAccessTokenService,
        JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.reservationPriceSnapshotRepository = reservationPriceSnapshotRepository;
        this.paymentRepository = paymentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.couponRepository = couponRepository;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void 같은_키와_사유의_순차_재시도는_최초_결과로_수렴하고_연계_상태를_유지한다() throws Exception {
        Fixture fixture = createCompleteFixture(ContentStatus.PUBLISHED);

        MvcResult first = request(fixture.operator(), fixture.contentId(), IDEMPOTENCY_KEY, "  운영 계획 변경  ")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn();
        MvcResult retry = request(fixture.operator(), fixture.contentId(), IDEMPOTENCY_KEY, "운영 계획 변경")
            .andExpect(status().isCreated())
            .andReturn();

        JsonNode firstData = objectMapper.readTree(first.getResponse().getContentAsString()).path("data");
        JsonNode retryData = objectMapper.readTree(retry.getResponse().getContentAsString()).path("data");
        assertThat(retryData).isEqualTo(firstData);
        assertThat(withdrawalRequestRepository.findAll()).singleElement().satisfies(request -> {
            assertThat(request.getContent().getContentId()).isEqualTo(fixture.contentId());
            assertThat(request.getRequestedBy().getUserId()).isEqualTo(fixture.operator().getUserId());
            assertThat(request.getStatus()).isEqualTo(ContentWithdrawalRequestStatus.PENDING);
            assertThat(request.getRequestReason()).isEqualTo("운영 계획 변경");
            assertThat(request.getIdempotencyKeyHash()).hasSize(64).doesNotContain(IDEMPOTENCY_KEY);
        });
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getReasonCode()).isEqualTo("CONTENT_WITHDRAWAL_REQUESTED");
                assertThat(event.getActorRole()).isEqualTo("OPERATOR");
                assertThat(auditEventActorLinkRepository.findById(event.getAuditEventId()))
                    .hasValueSatisfying(link -> assertThat(link.getActor().getUserId())
                        .isEqualTo(fixture.operator().getUserId()));
            });
        assertRelatedStateUnchanged(fixture);
    }

    @Test
    void 소유하지_않은_운영자는_요청할_수_없다() throws Exception {
        Fixture fixture = createCompleteFixture(ContentStatus.PUBLISHED);
        AppUser otherOperator = saveUser("other-operator");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            otherOperator,
            UserRole.OPERATOR,
            regionRepository.findById(fixture.regionId()).orElseThrow()
        ));

        request(otherOperator, fixture.contentId(), IDEMPOTENCY_KEY, "운영 계획 변경")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(withdrawalRequestRepository.count()).isZero();
    }

    @Test
    void 비_PUBLISHED_콘텐츠는_상태_충돌이다() throws Exception {
        Fixture fixture = createCompleteFixture(ContentStatus.ENDED);

        request(fixture.operator(), fixture.contentId(), IDEMPOTENCY_KEY, "운영 계획 변경")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertThat(withdrawalRequestRepository.count()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions request(
        AppUser operator,
        Long contentId,
        String idempotencyKey,
        String reason
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/operator/contents/{contentId}/withdrawal-requests", contentId)
            .header("Authorization", bearerToken(operator))
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"reason":"%s"}
                """.formatted(reason)));
    }

    private Fixture createCompleteFixture(ContentStatus contentStatus) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser operator = saveUser("operator-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        AppUser visitor = saveUser("visitor-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            contentStatus,
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
            now.minusSeconds(86_400)
        ));
        ContentRevision revision = contentRevisionRepository.saveAndFlush(new ContentRevision(
            content,
            1,
            content.getVersionNo(),
            operator,
            ContentRevisionStatus.EDIT_REQUESTED,
            "수정 제목",
            "수정 설명",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전 수칙",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소",
            RESERVATION_PRICE,
            null,
            now,
            null,
            null,
            null,
            null,
            null,
            null
        ));
        ContentSession session = new ContentSession(
            content,
            region,
            now.plusSeconds(3_600),
            now.plusSeconds(10_800),
            now.plusSeconds(1_800),
            now.plusSeconds(9_000),
            10
        );
        session.approve(operator, now);
        session = contentSessionRepository.saveAndFlush(session);
        Coupon coupon = saveReservedCoupon(content, region, visitor, now);
        CapacityHold activeHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            visitor,
            1,
            CapacityHoldStatus.ACTIVE,
            now.plusSeconds(600),
            null,
            null,
            null,
            now
        ));
        ReservationPriceSnapshot snapshot = reservationPriceSnapshotRepository.saveAndFlush(
            new ReservationPriceSnapshot(
                activeHold,
                coupon,
                RESERVATION_PRICE,
                1_000,
                RESERVATION_PRICE - 1_000,
                "KRW",
                now
            )
        );
        Payment payment = paymentRepository.saveAndFlush(new Payment(
            activeHold,
            snapshot,
            "order-" + suffix,
            now
        ));
        CapacityHold consumedHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            visitor,
            1,
            CapacityHoldStatus.CONSUMED,
            now.plusSeconds(600),
            now,
            null,
            null,
            now
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "reservation-" + suffix,
            "qr-" + suffix,
            region,
            consumedHold,
            session,
            visitor,
            ReservationStatus.CONFIRMED,
            now,
            null,
            null,
            null,
            null
        ));
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = remaining_capacity - 2 WHERE session_id = ?",
            session.getSessionId()
        );
        return new Fixture(
            region.getRegionId(),
            operator,
            content.getContentId(),
            content.getVersionNo(),
            revision.getContentRevisionId(),
            session.getSessionId(),
            activeHold.getHoldId(),
            consumedHold.getHoldId(),
            reservation.getReservationId(),
            payment.getPaymentId(),
            coupon.getCouponId()
        );
    }

    private Coupon saveReservedCoupon(
        Content content,
        Region region,
        AppUser visitor,
        Instant now
    ) {
        CouponPolicy policy = new CouponPolicy(
            content,
            region,
            "철회 요청 상태 유지 쿠폰",
            null,
            CouponIssuanceType.VISIT,
            1_000,
            1_000,
            30,
            now.minusSeconds(3_600),
            now.plusSeconds(3_600),
            null
        );
        policy.publish(now);
        Coupon coupon = new Coupon(
            couponPolicyRepository.saveAndFlush(policy),
            visitor,
            now,
            now.plusSeconds(3_600)
        );
        coupon.reserve();
        return couponRepository.saveAndFlush(coupon);
    }

    private void assertRelatedStateUnchanged(Fixture fixture) {
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> {
                assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
                assertThat(content.getVersionNo()).isEqualTo(fixture.contentVersion());
            });
        assertThat(contentRevisionRepository.findById(fixture.revisionId()))
            .hasValueSatisfying(revision -> assertThat(revision.getStatus())
                .isEqualTo(ContentRevisionStatus.EDIT_REQUESTED));
        assertThat(contentSessionRepository.findById(fixture.sessionId()))
            .hasValueSatisfying(session -> {
                assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.SCHEDULED);
                assertThat(session.getRemainingCapacity()).isEqualTo(8);
            });
        assertThat(capacityHoldRepository.findById(fixture.activeHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE));
        assertThat(capacityHoldRepository.findById(fixture.consumedHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED));
        assertThat(reservationRepository.findById(fixture.reservationId()))
            .hasValueSatisfying(reservation -> assertThat(reservation.getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED));
        assertThat(paymentRepository.findById(fixture.paymentId()))
            .hasValueSatisfying(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING));
        assertThat(couponRepository.findById(fixture.couponId()))
            .hasValueSatisfying(coupon -> assertThat(coupon.getStatus()).isEqualTo(CouponStatus.RESERVED));
    }

    private AppUser saveUser(String prefix) {
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, user.getUserId());
    }

    private record Fixture(
        Long regionId,
        AppUser operator,
        Long contentId,
        Integer contentVersion,
        Long revisionId,
        Long sessionId,
        Long activeHoldId,
        Long consumedHoldId,
        Long reservationId,
        Long paymentId,
        Long couponId
    ) {
    }
}
