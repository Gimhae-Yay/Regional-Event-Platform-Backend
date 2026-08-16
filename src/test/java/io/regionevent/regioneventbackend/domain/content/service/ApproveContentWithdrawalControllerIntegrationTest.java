package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionInvalidationReason;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
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
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponStatusHistoryRepository;
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
import io.regionevent.regioneventbackend.domain.review.entity.Review;
import io.regionevent.regioneventbackend.domain.review.entity.ReviewStatus;
import io.regionevent.regioneventbackend.domain.review.repository.ReviewRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest(properties = {
    "reservation.hold-termination.initial-delay=PT24H",
    "reservation.no-show-completion.initial-delay=PT24H"
})
@Testcontainers(disabledWithoutDocker = true)
class ApproveContentWithdrawalControllerIntegrationTest extends NonTransactionalMySqlTestSupport {

    private final ApproveContentWithdrawalUseCase useCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository roleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentLogRepository contentLogRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentWithdrawalRequestRepository withdrawalRequestRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationPriceSnapshotRepository reservationPriceSnapshotRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final CouponRepository couponRepository;
    private final CouponStatusHistoryRepository couponStatusHistoryRepository;
    private final VisitRepository visitRepository;
    private final ReviewRepository reviewRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PublicCatalogCacheInvalidator cacheInvalidator;

    @Autowired
    ApproveContentWithdrawalControllerIntegrationTest(
        ApproveContentWithdrawalUseCase useCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository roleAssignmentRepository,
        ContentRepository contentRepository,
        ContentLogRepository contentLogRepository,
        ContentRevisionRepository contentRevisionRepository,
        ContentSessionRepository contentSessionRepository,
        ContentWithdrawalRequestRepository withdrawalRequestRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationPriceSnapshotRepository reservationPriceSnapshotRepository,
        ReservationRepository reservationRepository,
        PaymentRepository paymentRepository,
        CouponPolicyRepository couponPolicyRepository,
        CouponRepository couponRepository,
        CouponStatusHistoryRepository couponStatusHistoryRepository,
        VisitRepository visitRepository,
        ReviewRepository reviewRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.useCase = useCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentLogRepository = contentLogRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationPriceSnapshotRepository = reservationPriceSnapshotRepository;
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.couponRepository = couponRepository;
        this.couponStatusHistoryRepository = couponStatusHistoryRepository;
        this.visitRepository = visitRepository;
        this.reviewRepository = reviewRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void 최초_승인과_자연_멱등_재시도가_저장_결과와_부수_효과를_보존한다() {
        Fixture fixture = createFixture();
        UUID firstRequestId = UUID.randomUUID();

        ApproveContentWithdrawalResult firstResult = useCase.approve(
            fixture.adminId(),
            fixture.withdrawalRequestId(),
            firstRequestId
        );
        ApproveContentWithdrawalResult retryResult = useCase.approve(
            fixture.adminId(),
            fixture.withdrawalRequestId(),
            UUID.randomUUID()
        );

        assertThat(retryResult).isEqualTo(firstResult);
        assertThat(firstResult.requestStatus()).isEqualTo(ContentWithdrawalRequestStatus.APPROVED);
        assertThat(firstResult.contentStatus()).isEqualTo(ContentStatus.WITHDRAWN);
        assertThat(firstResult.withdrawalReason()).isEqualTo("운영 계획 변경");
        assertThat(firstResult.approvedAt()).isNotNull();
        assertThat(withdrawalRequestRepository.findById(fixture.withdrawalRequestId()))
            .hasValueSatisfying(request -> {
                assertThat(request.getStatus()).isEqualTo(ContentWithdrawalRequestStatus.APPROVED);
                assertThat(request.getReviewedBy().getUserId()).isEqualTo(fixture.adminId());
                assertThat(request.getReviewedAt()).isEqualTo(firstResult.approvedAt());
                assertThat(request.getRequestReason()).isEqualTo("운영 계획 변경");
            });
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> {
                assertThat(content.getStatus()).isEqualTo(ContentStatus.WITHDRAWN);
                assertThat(content.getTitle()).isEqualTo("김해 가야 문화 체험");
            });
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED, ContentLogStatus.WITHDRAWN);
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .last()
            .satisfies(log -> {
                assertThat(log.getReason()).isEqualTo("운영 계획 변경");
                assertThat(log.getActor().getUserId()).isEqualTo(fixture.adminId());
                assertThat(log.getDate()).isEqualTo(firstResult.approvedAt());
            });
        assertThat(contentRevisionRepository.findById(fixture.revisionId()))
            .hasValueSatisfying(revision -> {
                assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_INVALIDATED);
                assertThat(revision.getInvalidationReason())
                    .isEqualTo(ContentRevisionInvalidationReason.CONTENT_WITHDRAWN);
                assertThat(revision.getInvalidatedAt()).isEqualTo(firstResult.approvedAt());
                assertThat(revision.getInvalidatedBy().getUserId()).isEqualTo(fixture.adminId());
            });
        assertThat(contentSessionRepository.findById(fixture.sessionId()))
            .hasValueSatisfying(session -> {
                assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.SCHEDULED);
                assertThat(session.getRemainingCapacity()).isEqualTo(8);
            });
        assertThat(capacityHoldRepository.findById(fixture.activeHoldId()))
            .hasValueSatisfying(hold -> {
                assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.INVALIDATED);
                assertThat(hold.getInvalidationReason()).isEqualTo("CONTENT_WITHDRAWN");
                assertThat(hold.getCapacityReleasedAt()).isNotNull();
            });
        assertThat(capacityHoldRepository.findById(fixture.confirmedHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED));
        assertThat(capacityHoldRepository.findById(fixture.checkedInHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED));
        assertThat(paymentRepository.findById(fixture.pendingPaymentId()))
            .hasValueSatisfying(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED));
        assertThat(paymentRepository.findById(fixture.approvedPaymentId()))
            .hasValueSatisfying(payment -> {
                assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
                assertThat(payment.getPortonePaymentId()).isEqualTo("payment-approved");
            });
        assertThat(couponRepository.findById(fixture.reservedCouponId()))
            .hasValueSatisfying(coupon -> assertThat(coupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE));
        assertThat(reservationPriceSnapshotRepository.findById(fixture.pendingSnapshotId()))
            .hasValueSatisfying(snapshot -> {
                assertThat(snapshot.getBaseAmount()).isEqualTo(10_000);
                assertThat(snapshot.getDiscountAmount()).isEqualTo(1_000);
                assertThat(snapshot.getFinalAmount()).isEqualTo(9_000);
                assertThat(snapshot.getCoupon().getCouponId()).isEqualTo(fixture.reservedCouponId());
            });
        assertThat(reservationPriceSnapshotRepository.count()).isEqualTo(3);
        assertThat(reservationRepository.findById(fixture.confirmedReservationId()))
            .hasValueSatisfying(reservation ->
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED)
            );
        assertThat(reservationRepository.findById(fixture.checkedInReservationId()))
            .hasValueSatisfying(reservation ->
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN)
            );
        assertThat(visitRepository.findById(fixture.visitId()))
            .hasValueSatisfying(visit ->
                assertThat(visit.getReservation().getReservationId())
                    .isEqualTo(fixture.checkedInReservationId())
            );
        assertThat(reviewRepository.findById(fixture.reviewId()))
            .hasValueSatisfying(review -> {
                assertThat(review.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
                assertThat(review.getReviewText()).isEqualTo("좋은 행사였습니다.");
            });
        assertThat(couponStatusHistoryRepository.count()).isOne();
        assertThat(auditEventRepository.findAll()).hasSize(6)
            .allSatisfy(auditEvent -> assertThat(auditEvent.getRequestId())
                .isEqualTo(firstRequestId.toString()));
        assertThat(auditEventRepository.findAll())
            .extracting(
                auditEvent -> auditEvent.getTargetType(),
                auditEvent -> auditEvent.getPreviousState(),
                auditEvent -> auditEvent.getNextState(),
                auditEvent -> auditEvent.getReasonCode()
            )
            .containsExactlyInAnyOrder(
                tuple(AuditEventTargetType.CONTENT, "EDIT_REQUESTED", "EDIT_INVALIDATED", "CONTENT_WITHDRAWN"),
                tuple(AuditEventTargetType.CAPACITY_HOLD, "ACTIVE", "INVALIDATED", "CONTENT_WITHDRAWN"),
                tuple(AuditEventTargetType.PAYMENT, "PENDING", "EXPIRED", "CONTENT_WITHDRAWN"),
                tuple(AuditEventTargetType.COUPON, "RESERVED", "AVAILABLE", "CAPACITY_HOLD_INVALIDATED"),
                tuple(
                    AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST,
                    "PENDING",
                    "APPROVED",
                    "CONTENT_WITHDRAWAL_APPROVED"
                ),
                tuple(AuditEventTargetType.CONTENT, "PUBLISHED", "WITHDRAWN", "CONTENT_WITHDRAWN")
            );
        assertThat(auditEventActorLinkRepository.count()).isEqualTo(6);
        verify(cacheInvalidator, times(1)).invalidateContentAfterCommit(
            fixture.regionId(),
            fixture.contentId(),
            0
        );
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser admin = saveUser("admin-" + suffix);
        roleAssignmentRepository.saveAndFlush(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
        AppUser operator = saveUser("operator-" + suffix);
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
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            now.minusSeconds(86_400)
        ));
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.PUBLISHED,
            null,
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
            "매일 11:00~19:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            0,
            null,
            now.minusSeconds(3_600),
            null,
            null,
            null,
            null,
            null,
            null
        ));
        ContentWithdrawalRequest request = withdrawalRequestRepository.saveAndFlush(
            ContentWithdrawalRequest.createPending(
                content,
                operator,
                "a".repeat(64),
                "운영 계획 변경",
                now.minusSeconds(1_800)
            )
        );
        ContentSession session = new ContentSession(
            content,
            region,
            now.plusSeconds(86_400),
            now.plusSeconds(93_600),
            now.plusSeconds(84_600),
            now.plusSeconds(91_800),
            10
        );
        session.approve(admin, now.minusSeconds(1_000));
        session = contentSessionRepository.saveAndFlush(session);
        AppUser visitor = saveUser("visitor-" + suffix);
        CouponPolicy couponPolicy = new CouponPolicy(
            content,
            region,
            "전체 철회 검증 쿠폰",
            "전체 철회 시 선점 해제 검증",
            CouponIssuanceType.VISIT,
            1_000,
            1_000,
            30,
            now.minusSeconds(3_600),
            now.plusSeconds(86_400),
            null
        );
        couponPolicy.publish(now.minusSeconds(3_000));
        couponPolicy = couponPolicyRepository.saveAndFlush(couponPolicy);
        Coupon reservedCoupon = new Coupon(
            couponPolicy,
            visitor,
            now.minusSeconds(2_000),
            now.plusSeconds(86_400)
        );
        reservedCoupon.reserve();
        reservedCoupon = couponRepository.saveAndFlush(reservedCoupon);
        CapacityHold activeHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            visitor,
            2,
            CapacityHoldStatus.ACTIVE,
            now.plusSeconds(600),
            null,
            null,
            null
        ));
        ReservationPriceSnapshot pendingSnapshot = reservationPriceSnapshotRepository.saveAndFlush(
            new ReservationPriceSnapshot(
                activeHold,
                reservedCoupon,
                10_000,
                1_000,
                9_000,
                "KRW",
                now.minusSeconds(1_000)
            )
        );
        Payment pendingPayment = paymentRepository.saveAndFlush(new Payment(
            activeHold,
            pendingSnapshot,
            "order-pending",
            now.minusSeconds(1_000)
        ));
        CapacityHold confirmedHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            visitor,
            1,
            CapacityHoldStatus.CONSUMED,
            now.plusSeconds(600),
            now.minusSeconds(900),
            null,
            null
        ));
        ReservationPriceSnapshot confirmedSnapshot = reservationPriceSnapshotRepository.saveAndFlush(
            new ReservationPriceSnapshot(
                confirmedHold,
                null,
                5_000,
                0,
                5_000,
                "KRW",
                now.minusSeconds(900)
            )
        );
        Reservation confirmedReservation = reservationRepository.saveAndFlush(new Reservation(
            "R-CONFIRMED-" + suffix,
            "qr-confirmed-" + suffix,
            region,
            confirmedHold,
            session,
            visitor,
            ReservationStatus.CONFIRMED,
            now.minusSeconds(800),
            null,
            null,
            null,
            null
        ));
        Payment approvedPayment = new Payment(
            confirmedHold,
            confirmedSnapshot,
            "order-approved",
            now.minusSeconds(900)
        );
        approvedPayment.approve(
            confirmedReservation,
            "payment-approved",
            now.minusSeconds(800)
        );
        approvedPayment = paymentRepository.saveAndFlush(approvedPayment);
        CapacityHold checkedInHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            visitor,
            1,
            CapacityHoldStatus.CONSUMED,
            now.plusSeconds(600),
            now.minusSeconds(700),
            null,
            null
        ));
        ReservationPriceSnapshot checkedInSnapshot = reservationPriceSnapshotRepository.saveAndFlush(
            new ReservationPriceSnapshot(
                checkedInHold,
                null,
                5_000,
                0,
                5_000,
                "KRW",
                now.minusSeconds(700)
            )
        );
        Reservation checkedInReservation = reservationRepository.saveAndFlush(new Reservation(
            "R-CHECKED-IN-" + suffix,
            "qr-checked-in-" + suffix,
            region,
            checkedInHold,
            session,
            visitor,
            ReservationStatus.CHECKED_IN,
            now.minusSeconds(600),
            null,
            null,
            null,
            null
        ));
        Visit visit = visitRepository.saveAndFlush(new Visit(
            region,
            checkedInReservation,
            visitor,
            content,
            session,
            admin,
            CheckinMethod.QR,
            now.minusSeconds(500)
        ));
        Review review = reviewRepository.saveAndFlush(new Review(
            region,
            visit,
            visitor,
            content,
            5,
            "좋은 행사였습니다.",
            ReviewStatus.PUBLISHED,
            null
        ));
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = remaining_capacity - 4 WHERE session_id = ?",
            session.getSessionId()
        );
        return new Fixture(
            admin.getUserId(),
            region.getRegionId(),
            content.getContentId(),
            revision.getContentRevisionId(),
            request.getContentWithdrawalRequestId(),
            session.getSessionId(),
            activeHold.getHoldId(),
            confirmedHold.getHoldId(),
            checkedInHold.getHoldId(),
            pendingSnapshot.getReservationPriceSnapshotId(),
            pendingPayment.getPaymentId(),
            approvedPayment.getPaymentId(),
            reservedCoupon.getCouponId(),
            confirmedReservation.getReservationId(),
            checkedInReservation.getReservationId(),
            visit.getVisitId(),
            review.getReviewId()
        );
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

    private record Fixture(
        Long adminId,
        Long regionId,
        Long contentId,
        Long revisionId,
        Long withdrawalRequestId,
        Long sessionId,
        Long activeHoldId,
        Long confirmedHoldId,
        Long checkedInHoldId,
        Long pendingSnapshotId,
        Long pendingPaymentId,
        Long approvedPaymentId,
        Long reservedCouponId,
        Long confirmedReservationId,
        Long checkedInReservationId,
        Long visitId,
        Long reviewId
    ) {
    }

}
