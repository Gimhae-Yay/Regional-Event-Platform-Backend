package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActorLinkService;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventService;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionInvalidationReason;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
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
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponStatusHistoryRepository;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotency;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentIdempotencyRepository;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentRepository;
import io.regionevent.regioneventbackend.domain.payment.service.PaymentIdempotencyService;
import io.regionevent.regioneventbackend.domain.payment.service.PaymentService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationPriceSnapshotRepository;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest(properties = {
    "reservation.hold-termination.initial-delay=PT24H",
    "reservation.no-show-completion.initial-delay=PT24H"
})
@Import(ApproveContentWithdrawalRollbackMySqlTest.FailingApprovalServicesConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class ApproveContentWithdrawalRollbackMySqlTest extends NonTransactionalMySqlTestSupport {

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
    private final ReservationPriceSnapshotRepository snapshotRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentIdempotencyRepository paymentIdempotencyRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final CouponRepository couponRepository;
    private final CouponStatusHistoryRepository couponStatusHistoryRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JdbcTemplate jdbcTemplate;
    private final FailureControl failureControl;

    @MockitoBean
    private PublicCatalogCacheInvalidator cacheInvalidator;

    @Autowired
    ApproveContentWithdrawalRollbackMySqlTest(
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
        ReservationPriceSnapshotRepository snapshotRepository,
        PaymentRepository paymentRepository,
        PaymentIdempotencyRepository paymentIdempotencyRepository,
        CouponPolicyRepository couponPolicyRepository,
        CouponRepository couponRepository,
        CouponStatusHistoryRepository couponStatusHistoryRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        JdbcTemplate jdbcTemplate,
        FailureControl failureControl
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
        this.snapshotRepository = snapshotRepository;
        this.paymentRepository = paymentRepository;
        this.paymentIdempotencyRepository = paymentIdempotencyRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.couponRepository = couponRepository;
        this.couponStatusHistoryRepository = couponStatusHistoryRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.failureControl = failureControl;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @AfterEach
    void resetFailureInjection() {
        failureControl.reset();
    }

    @ParameterizedTest(name = "{0} 저장 실패")
    @EnumSource(FailurePoint.class)
    void 승인_저장_단계별_실패는_업무와_성공_감사를_롤백하고_실패_감사만_독립_저장한다(
        FailurePoint failurePoint
    ) {
        Fixture fixture = createFixture();
        UUID requestId = UUID.randomUUID();
        failureControl.failAt(failurePoint);

        assertThatThrownBy(() -> useCase.approve(
            fixture.adminId(),
            fixture.withdrawalRequestId(),
            requestId
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage(failurePoint.failureMessage());

        assertBusinessStateRolledBack(fixture);
        assertFailureAuditCommitted(fixture, requestId);
    }

    private void assertBusinessStateRolledBack(Fixture fixture) {
        assertThat(withdrawalRequestRepository.findById(fixture.withdrawalRequestId()))
            .hasValueSatisfying(request -> {
                assertThat(request.getStatus()).isEqualTo(ContentWithdrawalRequestStatus.PENDING);
                assertThat(request.getReviewedAt()).isNull();
                assertThat(request.getReviewedBy()).isNull();
            });
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus())
                .isEqualTo(ContentStatus.PUBLISHED));
        assertThat(contentRevisionRepository.findById(fixture.revisionId()))
            .hasValueSatisfying(revision -> {
                assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
                assertThat(revision.getInvalidationReason()).isNull();
                assertThat(revision.getInvalidatedAt()).isNull();
            });
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED);
        assertThat(contentSessionRepository.findById(fixture.sessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(8));
        assertThat(capacityHoldRepository.findById(fixture.holdId()))
            .hasValueSatisfying(hold -> {
                assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE);
                assertThat(hold.getTerminalAt()).isNull();
                assertThat(hold.getCapacityReleasedAt()).isNull();
            });
        assertThat(paymentRepository.findById(fixture.paymentId()))
            .hasValueSatisfying(payment -> {
                assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
                assertThat(payment.getFinalizedAt()).isNull();
            });
        assertThat(paymentIdempotencyRepository.findById(fixture.paymentIdempotencyId()))
            .hasValueSatisfying(idempotency -> assertThat(idempotency.getExpiresAt()).isNull());
        assertThat(couponRepository.findById(fixture.couponId()))
            .hasValueSatisfying(coupon -> assertThat(coupon.getStatus()).isEqualTo(CouponStatus.RESERVED));
        assertThat(couponStatusHistoryRepository.count()).isZero();
        assertThat(snapshotRepository.findById(fixture.snapshotId())).isPresent();
    }

    private void assertFailureAuditCommitted(Fixture fixture, UUID requestId) {
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRequestId()).isEqualTo(requestId.toString());
            assertThat(auditEvent.getTargetType())
                .isEqualTo(AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST);
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.withdrawalRequestId());
            assertThat(auditEvent.getPreviousState()).isEqualTo(ContentWithdrawalRequestStatus.PENDING.name());
            assertThat(auditEvent.getNextState()).isNull();
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(auditEvent.getReasonCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.code());
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                .hasValueSatisfying(link -> assertThat(link.getActor().getUserId())
                    .isEqualTo(fixture.adminId()));
        });
        assertThat(auditEventActorLinkRepository.count()).isOne();
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("ROLLBACK-" + suffix, "김해시", true));
        AppUser admin = saveUser("admin-" + suffix);
        roleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            admin,
            UserRole.REGION_ADMIN,
            region
        ));
        AppUser operator = saveUser("operator-" + suffix);
        AppUser visitor = saveUser("visitor-" + suffix);
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
            now.plusSeconds(3_600),
            now.plusSeconds(7_200),
            now.plusSeconds(1_800),
            now.plusSeconds(5_400),
            10
        );
        session.approve(admin, now.minusSeconds(600));
        session = contentSessionRepository.saveAndFlush(session);
        CouponPolicy couponPolicy = new CouponPolicy(
            content,
            region,
            "롤백 검증 쿠폰",
            null,
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
        Coupon coupon = new Coupon(couponPolicy, visitor, now.minusSeconds(2_000), now.plusSeconds(86_400));
        coupon.reserve();
        coupon = couponRepository.saveAndFlush(coupon);
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            visitor,
            2,
            CapacityHoldStatus.ACTIVE,
            now.plusSeconds(600),
            null,
            null,
            null,
            now.minusSeconds(1_000)
        ));
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = remaining_capacity - 2 WHERE session_id = ?",
            session.getSessionId()
        );
        ReservationPriceSnapshot snapshot = snapshotRepository.saveAndFlush(new ReservationPriceSnapshot(
            hold,
            coupon,
            10_000,
            1_000,
            9_000,
            "KRW",
            now.minusSeconds(1_000)
        ));
        Payment payment = paymentRepository.saveAndFlush(new Payment(
            hold,
            snapshot,
            "order-rollback-" + suffix,
            now.minusSeconds(1_000)
        ));
        PaymentIdempotency paymentIdempotency = new PaymentIdempotency(
            visitor.getUserId(),
            "payment-key-" + suffix,
            "payment-request-" + suffix
        );
        paymentIdempotency.succeedWithPayment(payment, now.minusSeconds(1_000));
        paymentIdempotency = paymentIdempotencyRepository.saveAndFlush(paymentIdempotency);
        return new Fixture(
            admin.getUserId(),
            content.getContentId(),
            revision.getContentRevisionId(),
            request.getContentWithdrawalRequestId(),
            session.getSessionId(),
            hold.getHoldId(),
            snapshot.getReservationPriceSnapshotId(),
            payment.getPaymentId(),
            paymentIdempotency.getPaymentIdempotencyId(),
            coupon.getCouponId()
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

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingApprovalServicesConfig {

        @Bean
        FailureControl failureControl() {
            return new FailureControl();
        }

        @Bean
        @Primary
        FailingContentWithdrawalRequestService failingContentWithdrawalRequestService(
            ContentWithdrawalRequestRepository repository,
            FailureControl failureControl
        ) {
            return new FailingContentWithdrawalRequestService(repository, failureControl);
        }

        @Bean
        @Primary
        FailingContentService failingContentService(
            ContentRepository repository,
            FailureControl failureControl
        ) {
            return new FailingContentService(repository, failureControl);
        }

        @Bean
        @Primary
        FailingContentRevisionInvalidationService failingContentRevisionInvalidationService(
            ContentRevisionRepository repository,
            FailureControl failureControl
        ) {
            return new FailingContentRevisionInvalidationService(repository, failureControl);
        }

        @Bean
        @Primary
        FailingContentLogService failingContentLogService(
            ContentLogRepository repository,
            FailureControl failureControl
        ) {
            return new FailingContentLogService(repository, failureControl);
        }

        @Bean
        @Primary
        FailingCapacityHoldService failingCapacityHoldService(
            CapacityHoldRepository repository,
            FailureControl failureControl
        ) {
            return new FailingCapacityHoldService(repository, failureControl);
        }

        @Bean
        @Primary
        FailingPaymentService failingPaymentService(
            PaymentRepository repository,
            FailureControl failureControl
        ) {
            return new FailingPaymentService(repository, failureControl);
        }

        @Bean
        @Primary
        FailingPaymentIdempotencyService failingPaymentIdempotencyService(
            PaymentIdempotencyRepository repository,
            FailureControl failureControl
        ) {
            return new FailingPaymentIdempotencyService(repository, failureControl);
        }

        @Bean
        @Primary
        FailingCouponStatusHistoryService failingCouponStatusHistoryService(
            CouponStatusHistoryRepository repository,
            FailureControl failureControl
        ) {
            return new FailingCouponStatusHistoryService(repository, failureControl);
        }

        @Bean
        @Primary
        FailingRecordAuditEventUseCase failingRecordAuditEventUseCase(
            AuditEventService auditEventService,
            AuditEventActorLinkService auditEventActorLinkService,
            FailureControl failureControl
        ) {
            return new FailingRecordAuditEventUseCase(
                auditEventService,
                auditEventActorLinkService,
                failureControl
            );
        }
    }

    static class FailureControl {

        private final AtomicReference<FailurePoint> failurePoint = new AtomicReference<>();

        void failAt(FailurePoint point) {
            if (!failurePoint.compareAndSet(null, point)) {
                throw new IllegalStateException("failure point is already configured");
            }
        }

        void failIf(FailurePoint point) {
            if (failurePoint.compareAndSet(point, null)) {
                throw new IllegalStateException(point.failureMessage());
            }
        }

        void reset() {
            failurePoint.set(null);
        }
    }

    static class FailingContentWithdrawalRequestService extends ContentWithdrawalRequestService {

        private final FailureControl failureControl;

        FailingContentWithdrawalRequestService(
            ContentWithdrawalRequestRepository repository,
            FailureControl failureControl
        ) {
            super(repository);
            this.failureControl = failureControl;
        }

        @Override
        public ContentWithdrawalRequest approve(
            ContentWithdrawalRequest request,
            AppUser reviewer,
            Instant reviewedAt
        ) {
            ContentWithdrawalRequest approved = super.approve(request, reviewer, reviewedAt);
            failureControl.failIf(FailurePoint.REQUEST);
            return approved;
        }
    }

    static class FailingContentService extends ContentService {

        private final FailureControl failureControl;

        FailingContentService(ContentRepository repository, FailureControl failureControl) {
            super(repository);
            this.failureControl = failureControl;
        }

        @Override
        public Content withdraw(Content content, Instant withdrawnAt) {
            Content withdrawn = super.withdraw(content, withdrawnAt);
            failureControl.failIf(FailurePoint.CONTENT);
            return withdrawn;
        }
    }

    static class FailingContentRevisionInvalidationService extends ContentRevisionInvalidationService {

        private final ContentRevisionRepository repository;
        private final FailureControl failureControl;

        FailingContentRevisionInvalidationService(
            ContentRevisionRepository repository,
            FailureControl failureControl
        ) {
            super(repository);
            this.repository = repository;
            this.failureControl = failureControl;
        }

        @Override
        public Optional<ContentRevision> invalidateActiveRevisionForContent(
            Long contentId,
            AppUser invalidator,
            Instant invalidatedAt,
            ContentRevisionInvalidationReason reason
        ) {
            Optional<ContentRevision> revision = super.invalidateActiveRevisionForContent(
                contentId,
                invalidator,
                invalidatedAt,
                reason
            );
            repository.flush();
            failureControl.failIf(FailurePoint.REVISION);
            return revision;
        }
    }

    static class FailingContentLogService extends ContentLogService {

        private final FailureControl failureControl;

        FailingContentLogService(ContentLogRepository repository, FailureControl failureControl) {
            super(repository);
            this.failureControl = failureControl;
        }

        @Override
        public ContentLog recordWithdrawn(
            Content content,
            AppUser actor,
            Instant withdrawnAt,
            String reason
        ) {
            ContentLog log = super.recordWithdrawn(content, actor, withdrawnAt, reason);
            failureControl.failIf(FailurePoint.CONTENT_LOG);
            return log;
        }
    }

    static class FailingCapacityHoldService extends CapacityHoldService {

        private final FailureControl failureControl;

        FailingCapacityHoldService(CapacityHoldRepository repository, FailureControl failureControl) {
            super(repository);
            this.failureControl = failureControl;
        }

        @Override
        public List<TerminatedCapacityHold> invalidateAllActiveHoldsForContent(
            Long contentId,
            String invalidationReason
        ) {
            List<TerminatedCapacityHold> holds = super.invalidateAllActiveHoldsForContent(
                contentId,
                invalidationReason
            );
            failureControl.failIf(FailurePoint.HOLD_AND_CAPACITY);
            return holds;
        }
    }

    static class FailingPaymentService extends PaymentService {

        private final PaymentRepository repository;
        private final FailureControl failureControl;

        FailingPaymentService(PaymentRepository repository, FailureControl failureControl) {
            super(repository);
            this.repository = repository;
            this.failureControl = failureControl;
        }

        @Override
        public Optional<Payment> expirePendingByHoldId(Long holdId, Instant expiredAt) {
            Optional<Payment> payment = super.expirePendingByHoldId(holdId, expiredAt);
            repository.flush();
            failureControl.failIf(FailurePoint.PAYMENT);
            return payment;
        }
    }

    static class FailingPaymentIdempotencyService extends PaymentIdempotencyService {

        private final PaymentIdempotencyRepository repository;
        private final FailureControl failureControl;

        FailingPaymentIdempotencyService(
            PaymentIdempotencyRepository repository,
            FailureControl failureControl
        ) {
            super(repository);
            this.repository = repository;
            this.failureControl = failureControl;
        }

        @Override
        public void setPaymentResultExpiration(Payment payment, Instant finalizedAt) {
            super.setPaymentResultExpiration(payment, finalizedAt);
            repository.flush();
            failureControl.failIf(FailurePoint.PAYMENT_IDEMPOTENCY);
        }
    }

    static class FailingCouponStatusHistoryService extends CouponStatusHistoryService {

        private final FailureControl failureControl;

        FailingCouponStatusHistoryService(
            CouponStatusHistoryRepository repository,
            FailureControl failureControl
        ) {
            super(repository);
            this.failureControl = failureControl;
        }

        @Override
        public CouponStatusHistory create(CouponStatusHistory history) {
            CouponStatusHistory created = super.create(history);
            failureControl.failIf(FailurePoint.COUPON_AND_HISTORY);
            return created;
        }
    }

    static class FailingRecordAuditEventUseCase extends RecordAuditEventUseCase {

        private final FailureControl failureControl;

        FailingRecordAuditEventUseCase(
            AuditEventService auditEventService,
            AuditEventActorLinkService auditEventActorLinkService,
            FailureControl failureControl
        ) {
            super(auditEventService, auditEventActorLinkService);
            this.failureControl = failureControl;
        }

        @Override
        @Transactional(propagation = Propagation.MANDATORY)
        public AuditEvent record(AuditEventCommand command) {
            AuditEvent auditEvent = super.record(command);
            if (command.targetType() == AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST) {
                failureControl.failIf(FailurePoint.SUCCESS_AUDIT);
            }
            return auditEvent;
        }
    }

    private enum FailurePoint {
        REQUEST("request storage failure"),
        CONTENT("content storage failure"),
        REVISION("revision storage failure"),
        CONTENT_LOG("content log storage failure"),
        HOLD_AND_CAPACITY("hold and capacity storage failure"),
        PAYMENT("payment storage failure"),
        PAYMENT_IDEMPOTENCY("payment idempotency storage failure"),
        COUPON_AND_HISTORY("coupon and history storage failure"),
        SUCCESS_AUDIT("success audit storage failure");

        private final String failureMessage;

        FailurePoint(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        private String failureMessage() {
            return failureMessage;
        }
    }

    private record Fixture(
        Long adminId,
        Long contentId,
        Long revisionId,
        Long withdrawalRequestId,
        Long sessionId,
        Long holdId,
        Long snapshotId,
        Long paymentId,
        Long paymentIdempotencyId,
        Long couponId
    ) {
    }
}
