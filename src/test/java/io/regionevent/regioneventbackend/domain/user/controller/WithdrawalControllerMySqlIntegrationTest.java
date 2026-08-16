package io.regionevent.regioneventbackend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuance;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponIssuanceRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponStatusHistoryRepository;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponExpirationResult;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.ExpireCouponsUseCase;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.operator.repository.OperatorApplicationRepository;
import io.regionevent.regioneventbackend.domain.payment.dto.CreatePaymentRequest;
import io.regionevent.regioneventbackend.domain.payment.dto.CreatePaymentResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentRepository;
import io.regionevent.regioneventbackend.domain.payment.repository.RefundRepository;
import io.regionevent.regioneventbackend.domain.payment.service.CreatePaymentUseCase;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationHoldRequest;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationPriceSnapshotRepository;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.reservation.service.CreateReservationHoldUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.ExpireOrInvalidateCapacityHoldsUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.GetMyReservationQrUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.HoldTerminationResult;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationCancellationUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationConfirmationUseCase;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.WithdrawUserUseCase;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(WithdrawalControllerMySqlIntegrationTest.WithdrawalMySqlTestConfiguration.class)
class WithdrawalControllerMySqlIntegrationTest extends NonTransactionalMySqlTestSupport {

    private static final String WITHDRAWAL_PATH = "/api/v1/auth/delete";
    private final MockMvc mockMvc;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RegionRepository regionRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationPriceSnapshotRepository reservationPriceSnapshotRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OperatorApplicationRepository operatorApplicationRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final CouponRepository couponRepository;
    private final CouponIssuanceRepository couponIssuanceRepository;
    private final CouponStatusHistoryRepository couponStatusHistoryRepository;
    private final VisitRepository visitRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final JdbcTemplate jdbcTemplate;
    private final WithdrawUserUseCase withdrawUserUseCase;
    private final ExpireOrInvalidateCapacityHoldsUseCase expireOrInvalidateCapacityHoldsUseCase;
    private final FailingWithdrawalCapacityHoldService failingWithdrawalCapacityHoldService;
    private final RefreshTokenStore refreshTokenStore;
    private final CreateReservationHoldUseCase createReservationHoldUseCase;
    private final ReservationConfirmationUseCase reservationConfirmationUseCase;
    private final ReservationCancellationUseCase reservationCancellationUseCase;
    private final GetMyReservationQrUseCase getMyReservationQrUseCase;
    private final CreatePaymentUseCase createPaymentUseCase;
    private final ExpireCouponsUseCase expireCouponsUseCase;

    private final CapacityHoldRepository capacityHoldRepository;
    private final TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private CouponService couponService;

    @Autowired
    WithdrawalControllerMySqlIntegrationTest(
        MockMvc mockMvc,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        RegionRepository regionRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        ReservationPriceSnapshotRepository reservationPriceSnapshotRepository,
        PaymentRepository paymentRepository,
        RefundRepository refundRepository,
        OperatorApplicationRepository operatorApplicationRepository,
        CouponPolicyRepository couponPolicyRepository,
        CouponRepository couponRepository,
        CouponIssuanceRepository couponIssuanceRepository,
        CouponStatusHistoryRepository couponStatusHistoryRepository,
        VisitRepository visitRepository,
        JwtAccessTokenService jwtAccessTokenService,
        JdbcTemplate jdbcTemplate,
        WithdrawUserUseCase withdrawUserUseCase,
        ExpireOrInvalidateCapacityHoldsUseCase expireOrInvalidateCapacityHoldsUseCase,
        FailingWithdrawalCapacityHoldService failingWithdrawalCapacityHoldService,
        RefreshTokenStore refreshTokenStore,
        CreateReservationHoldUseCase createReservationHoldUseCase,
        ReservationConfirmationUseCase reservationConfirmationUseCase,
        ReservationCancellationUseCase reservationCancellationUseCase,
        GetMyReservationQrUseCase getMyReservationQrUseCase,
        CreatePaymentUseCase createPaymentUseCase,
        ExpireCouponsUseCase expireCouponsUseCase,
        PlatformTransactionManager transactionManager
    ) {
        this.mockMvc = mockMvc;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.regionRepository = regionRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.reservationPriceSnapshotRepository = reservationPriceSnapshotRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.operatorApplicationRepository = operatorApplicationRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.couponRepository = couponRepository;
        this.couponIssuanceRepository = couponIssuanceRepository;
        this.couponStatusHistoryRepository = couponStatusHistoryRepository;
        this.visitRepository = visitRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.jdbcTemplate = jdbcTemplate;
        this.withdrawUserUseCase = withdrawUserUseCase;
        this.expireOrInvalidateCapacityHoldsUseCase = expireOrInvalidateCapacityHoldsUseCase;
        this.failingWithdrawalCapacityHoldService = failingWithdrawalCapacityHoldService;
        this.refreshTokenStore = refreshTokenStore;
        this.createReservationHoldUseCase = createReservationHoldUseCase;
        this.reservationConfirmationUseCase = reservationConfirmationUseCase;
        this.reservationCancellationUseCase = reservationCancellationUseCase;
        this.getMyReservationQrUseCase = getMyReservationQrUseCase;
        this.createPaymentUseCase = createPaymentUseCase;
        this.expireCouponsUseCase = expireCouponsUseCase;
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @BeforeEach
    void setUp() {
        reset(refreshTokenStore, AopTestUtils.<CouponService>getTargetObject(couponService));
        failingWithdrawalCapacityHoldService.reset();
    }

    @Test
    void withdraw_withActiveHoldAndConfirmedReservation_terminatesBothAndRestoresCapacity() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(delete(WITHDRAWAL_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(fixture.user().getUserId())))
            .andExpect(status().isOk());

        assertThat(appUserRepository.findById(fixture.user().getUserId())).isEmpty();
        assertThat(capacityHoldRepository.findById(fixture.activeHold().getHoldId()))
            .hasValueSatisfying(hold -> {
                assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.INVALIDATED);
                assertThat(hold.getUser()).isNull();
                assertThat(hold.getInvalidationReason()).isEqualTo("USER_WITHDRAWAL");
            });
        assertThat(reservationRepository.findById(fixture.reservation().getReservationId()))
            .hasValueSatisfying(reservation -> {
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(reservation.getUser()).isNull();
                assertThat(reservation.getCancellationReason()).isEqualTo("USER_WITHDRAWAL");
                assertThat(reservation.getCapacityReleasedAt()).isNotNull();
            });
        assertThat(contentSessionRepository.findById(fixture.session().getSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(10));
    }

    @Test
    void withdraw_withExpiredActiveHold_invalidatesHoldAndRestoresCapacity() throws Exception {
        Fixture fixture = createFixture();
        expireActiveHold(fixture);

        mockMvc.perform(delete(WITHDRAWAL_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(fixture.user().getUserId())))
            .andExpect(status().isOk());

        assertThat(appUserRepository.findById(fixture.user().getUserId())).isEmpty();
        assertThat(capacityHoldRepository.findById(fixture.activeHold().getHoldId()))
            .hasValueSatisfying(hold -> {
                assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.INVALIDATED);
                assertThat(hold.getUser()).isNull();
                assertThat(hold.getInvalidationReason()).isEqualTo("USER_WITHDRAWAL");
                assertThat(hold.getTerminalAt()).isNotNull();
                assertThat(hold.getCapacityReleasedAt()).isNotNull();
            });
        assertThat(contentSessionRepository.findById(fixture.session().getSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(10));
    }

    @Test
    void withdraw_withCoupons_invalidatesOnlyActiveCouponsAndUnlinksCouponData() throws Exception {
        Fixture fixture = createFixture();
        CouponWithdrawalFixture couponFixture = createCouponWithdrawalFixture(fixture);

        mockMvc.perform(delete(WITHDRAWAL_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(fixture.user().getUserId())))
            .andExpect(status().isOk());

        assertCouponStatusAndUnlinkedUser(couponFixture.availableCouponId(), CouponStatus.INVALIDATED);
        assertCouponStatusAndUnlinkedUser(couponFixture.reservedCouponId(), CouponStatus.INVALIDATED);
        assertCouponStatusAndUnlinkedUser(couponFixture.usedCouponId(), CouponStatus.USED);
        assertCouponStatusAndUnlinkedUser(couponFixture.expiredCouponId(), CouponStatus.EXPIRED);
        assertCouponStatusAndUnlinkedUser(couponFixture.invalidatedCouponId(), CouponStatus.INVALIDATED);
        assertThat(couponIssuanceRepository.findById(couponFixture.couponIssuanceId()))
            .hasValueSatisfying(issuance -> assertThat(issuance.getRecipientUser()).isNull());
        assertThat(couponIssuanceRepository.findById(couponFixture.usedCouponIssuanceId()))
            .hasValueSatisfying(issuance -> assertThat(issuance.getRecipientUser()).isNull());
        assertCouponInvalidationHistory(couponFixture.availableCouponId(), CouponStatus.AVAILABLE);
        assertCouponInvalidationHistory(couponFixture.reservedCouponId(), CouponStatus.RESERVED);
        assertPreservedTerminalCouponHistories(couponFixture);
    }

    @Test
    void withdraw_whenLaterTerminationFails_rollsBackCouponInvalidationHistoryAndUnlinking() {
        Fixture fixture = createFixture();
        CouponWithdrawalFixture couponFixture = createCouponWithdrawalFixture(fixture);
        failingWithdrawalCapacityHoldService.failAfterNextWithdrawalTermination();

        assertThatThrownBy(() -> withdrawUserUseCase.withdraw(fixture.user().getUserId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("simulated withdrawal capacity hold termination failure");

        assertThat(couponRepository.findById(couponFixture.availableCouponId()))
            .hasValueSatisfying(coupon -> {
                assertThat(coupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
                assertThat(coupon.getUser()).isNotNull();
            });
        assertThat(couponRepository.findById(couponFixture.reservedCouponId()))
            .hasValueSatisfying(coupon -> {
                assertThat(coupon.getStatus()).isEqualTo(CouponStatus.RESERVED);
                assertThat(coupon.getUser()).isNotNull();
            });
        assertThat(couponIssuanceRepository.findById(couponFixture.couponIssuanceId()))
            .hasValueSatisfying(issuance -> assertThat(issuance.getRecipientUser()).isNotNull());
        assertThat(couponStatusHistoryRepository.findAllByCouponCouponIdOrderByOccurredAtAsc(couponFixture.availableCouponId()))
            .isEmpty();
        assertThat(couponStatusHistoryRepository.findAllByCouponCouponIdOrderByOccurredAtAsc(couponFixture.reservedCouponId()))
            .isEmpty();
        assertPreservedTerminalCouponHistories(couponFixture);
    }

    @Test
    @Timeout(10)
    @SuppressWarnings("unchecked")
    void withdraw_whenCouponExpirationRaces_keepsOneTerminalTransitionHistory() throws Exception {
        Fixture fixture = createFixture();
        Long couponId = createExpiredAvailableCoupon(fixture);
        CountDownLatch expirationCandidateRead = new CountDownLatch(1);
        CountDownLatch releaseExpiration = new CountDownLatch(1);
        doAnswer(invocation -> {
            List<Long> couponIds = (List<Long>) invocation.callRealMethod();
            expirationCandidateRead.countDown();
            await(releaseExpiration);
            return couponIds;
        }).when(AopTestUtils.<CouponService>getTargetObject(couponService)).findExpirationCandidateIds(anyInt());

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<CouponExpirationResult> expiration = executorService.submit(expireCouponsUseCase::execute);
            assertThat(expirationCandidateRead.await(3, TimeUnit.SECONDS)).isTrue();

            Future<?> withdrawal = executorService.submit(
                () -> withdrawUserUseCase.withdraw(fixture.user().getUserId())
            );
            withdrawal.get(5, TimeUnit.SECONDS);
            releaseExpiration.countDown();
            assertThat(expiration.get(5, TimeUnit.SECONDS).expiredCouponCount()).isZero();
        }

        Coupon coupon = couponRepository.findById(couponId).orElseThrow();
        List<CouponStatusHistory> histories = couponStatusHistoryRepository
            .findAllByCouponCouponIdOrderByOccurredAtAsc(couponId);
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.INVALIDATED);
        assertThat(coupon.getUser()).isNull();
        assertThat(histories).singleElement().satisfies(history -> {
            assertThat(history.getPreviousStatus()).isEqualTo(CouponStatus.AVAILABLE);
            assertThat(history.getNextStatus()).isEqualTo(CouponStatus.INVALIDATED);
            assertThat(history.getReasonCode()).isEqualTo("USER_WITHDRAWAL");
            assertThat(history.getActorKind()).isEqualTo("USER");
        });
    }

    @Test
    void withdraw_whenSchedulerAlreadyExpiredHold_doesNotReleaseCapacityTwice() throws Exception {
        Fixture fixture = createFixture();
        expireActiveHold(fixture);
        expireOrInvalidateCapacityHoldsUseCase.execute();

        mockMvc.perform(delete(WITHDRAWAL_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(fixture.user().getUserId())))
            .andExpect(status().isOk());

        assertThat(capacityHoldRepository.findById(fixture.activeHold().getHoldId()))
            .hasValueSatisfying(hold -> {
                assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.EXPIRED);
                assertThat(hold.getUser()).isNull();
                assertThat(hold.getCapacityReleasedAt()).isNotNull();
            });
        assertThat(contentSessionRepository.findById(fixture.session().getSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(10));
    }

    @Test
    @Timeout(10)
    void withdraw_whenSchedulerTerminatesExpiredHoldAfterSnapshot_completesWithoutSchedulerFailure() throws Exception {
        Fixture fixture = createFixture();
        expireActiveHold(fixture);
        CountDownLatch withdrawalSnapshotRead = new CountDownLatch(1);
        CountDownLatch releaseWithdrawal = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Void> withdrawal = executorService.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    capacityHoldRepository.findActiveHoldIdsByUserId(fixture.user().getUserId());
                    withdrawalSnapshotRead.countDown();
                    await(releaseWithdrawal);
                    withdrawUserUseCase.withdraw(fixture.user().getUserId());
                });
                return null;
            });
            assertThat(withdrawalSnapshotRead.await(3, TimeUnit.SECONDS)).isTrue();

            Future<HoldTerminationResult> scheduler = executorService.submit(
                expireOrInvalidateCapacityHoldsUseCase::execute
            );
            try {
                HoldTerminationResult schedulerResult = scheduler.get(5, TimeUnit.SECONDS);
                assertThat(schedulerResult.expiredHoldCount()).isOne();
                assertThat(schedulerResult.invalidatedHoldCount()).isZero();
                assertThat(schedulerResult.failedHoldCount()).isZero();
            } finally {
                releaseWithdrawal.countDown();
            }
            withdrawal.get(5, TimeUnit.SECONDS);
        }

        assertThat(appUserRepository.findById(fixture.user().getUserId())).isEmpty();
        assertThat(capacityHoldRepository.findById(fixture.activeHold().getHoldId()))
            .hasValueSatisfying(hold -> {
                assertThat(hold.getStatus()).isIn(CapacityHoldStatus.EXPIRED, CapacityHoldStatus.INVALIDATED);
                assertThat(hold.getUser()).isNull();
                assertThat(hold.getTerminalAt()).isNotNull();
                assertThat(hold.getCapacityReleasedAt()).isNotNull();
            });
        assertThat(contentSessionRepository.findById(fixture.session().getSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(10));
    }

    @Test
    void withdraw_whenHoldTerminationFails_rollsBackExpiredActiveHoldAndCapacity() {
        Fixture fixture = createFixture();
        expireActiveHold(fixture);
        failingWithdrawalCapacityHoldService.failAfterNextWithdrawalTermination();

        assertThatThrownBy(() -> withdrawUserUseCase.withdraw(fixture.user().getUserId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("simulated withdrawal capacity hold termination failure");

        assertThat(appUserRepository.findById(fixture.user().getUserId()))
            .hasValueSatisfying(user -> assertThat(user.getStatus()).isEqualTo(AppUserStatus.ACTIVE));
        assertThat(capacityHoldRepository.findById(fixture.activeHold().getHoldId()))
            .hasValueSatisfying(hold -> {
                assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE);
                assertThat(hold.getUser()).isNotNull();
                assertThat(hold.getTerminalAt()).isNull();
                assertThat(hold.getCapacityReleasedAt()).isNull();
            });
        assertThat(contentSessionRepository.findById(fixture.session().getSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(8));
    }

    @Test
    void withdraw_withPendingPayment_rejectsWithoutChangingThePaymentOrHold() {
        Fixture fixture = createFixture();
        jdbcTemplate.update(
            "UPDATE content SET reservation_price = ? WHERE content_id = ?",
            20_000,
            fixture.session().getContent().getContentId()
        );
        createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.activeHold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            "withdrawal-pending-payment-" + System.nanoTime(),
            UUID.randomUUID()
        );

        assertThat(withdraw(fixture.user().getUserId())).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(appUserRepository.findById(fixture.user().getUserId())).isPresent();
        assertThat(capacityHoldRepository.findById(fixture.activeHold().getHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE));
        assertThat(paymentRepository.findAll()).singleElement()
            .extracting(payment -> payment.getStatus())
            .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void withdraw_withApprovedPaymentAndConfirmedReservation_rejectsWithoutChangingPaymentReservationOrHold() {
        Fixture fixture = createFixture();
        Payment payment = createApprovedPayment(fixture);
        CapacityHold consumedHold = fixture.reservation().getCapacityHold();

        assertThat(withdraw(fixture.user().getUserId())).isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(appUserRepository.findById(fixture.user().getUserId()))
            .hasValueSatisfying(user -> assertThat(user.getStatus()).isEqualTo(AppUserStatus.ACTIVE));
        assertThat(capacityHoldRepository.findById(consumedHold.getHoldId()))
            .hasValueSatisfying(hold -> {
                assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED);
                assertThat(hold.getUser()).isNotNull();
            });
        assertThat(reservationRepository.findById(fixture.reservation().getReservationId()))
            .hasValueSatisfying(reservation -> {
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
                assertThat(reservation.getUser()).isNotNull();
            });
        Long paymentId = payment.getPaymentId();
        assertThat(paymentRepository.findByPaymentId(paymentId))
            .hasValueSatisfying(savedPayment -> {
                assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
                assertThat(savedPayment.getReservation().getReservationId())
                    .isEqualTo(fixture.reservation().getReservationId());
            });
        assertThat(refundRepository.findAll()).isEmpty();
        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    void withdraw_withSucceededRefundAndConfirmedReservation_cancelsReservationAndDeletesAccount() {
        Fixture fixture = createFixture();
        Payment payment = createApprovedPayment(fixture);
        Refund refund = createTerminalRefund(payment, RefundStatus.SUCCEEDED);

        assertThat(withdraw(fixture.user().getUserId())).isNull();

        assertThat(appUserRepository.findById(fixture.user().getUserId())).isEmpty();
        assertThat(capacityHoldRepository.findById(fixture.reservation().getCapacityHold().getHoldId()))
            .hasValueSatisfying(hold -> {
                assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED);
                assertThat(hold.getUser()).isNull();
            });
        assertThat(reservationRepository.findById(fixture.reservation().getReservationId()))
            .hasValueSatisfying(reservation -> {
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(reservation.getUser()).isNull();
                assertThat(reservation.getCancellationReason()).isEqualTo("USER_WITHDRAWAL");
            });
        assertThat(paymentRepository.findByPaymentId(payment.getPaymentId()))
            .hasValueSatisfying(savedPayment -> assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED));
        assertThat(refundRepository.findById(refund.getRefundId()))
            .hasValueSatisfying(savedRefund -> assertThat(savedRefund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED));
    }

    @ParameterizedTest
    @EnumSource(value = RefundStatus.class, names = {"FAILED", "DISCREPANT"})
    void withdraw_withUnresolvedRefundAndConfirmedReservation_rejectsWithoutChangingPaymentReservationOrHold(
        RefundStatus refundStatus
    ) {
        Fixture fixture = createFixture();
        Payment payment = createApprovedPayment(fixture);
        Refund refund = createTerminalRefund(payment, refundStatus);
        CapacityHold consumedHold = fixture.reservation().getCapacityHold();

        assertThat(withdraw(fixture.user().getUserId())).isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(appUserRepository.findById(fixture.user().getUserId()))
            .hasValueSatisfying(user -> assertThat(user.getStatus()).isEqualTo(AppUserStatus.ACTIVE));
        assertThat(capacityHoldRepository.findById(consumedHold.getHoldId()))
            .hasValueSatisfying(hold -> {
                assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED);
                assertThat(hold.getUser()).isNotNull();
            });
        assertThat(reservationRepository.findById(fixture.reservation().getReservationId()))
            .hasValueSatisfying(reservation -> {
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
                assertThat(reservation.getUser()).isNotNull();
            });
        assertThat(paymentRepository.findByPaymentId(payment.getPaymentId()))
            .hasValueSatisfying(savedPayment -> assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED));
        assertThat(refundRepository.findById(refund.getRefundId()))
            .hasValueSatisfying(savedRefund -> assertThat(savedRefund.getStatus()).isEqualTo(refundStatus));
        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    void withdraw_withRequestedRefund_rejectsWithoutChangingThePaymentOrHold() {
        Fixture fixture = createFixture();
        jdbcTemplate.update(
            "UPDATE content SET reservation_price = ? WHERE content_id = ?",
            20_000,
            fixture.session().getContent().getContentId()
        );
        CreatePaymentResponse paymentResponse = createPaymentUseCase.create(
            fixture.user().getUserId(),
            fixture.activeHold().getHoldId().toString(),
            new CreatePaymentRequest(null),
            "withdrawal-requested-refund-" + System.nanoTime(),
            UUID.randomUUID()
        );
        jdbcTemplate.update(
            "UPDATE payment SET status = 'APPROVED', finalized_at = CURRENT_TIMESTAMP(6) WHERE hold_id = ?",
            fixture.activeHold().getHoldId()
        );
        refundRepository.saveAndFlush(new Refund(
            paymentRepository.findByOrderId(paymentResponse.payment().orderId()).orElseThrow(),
            20_000,
            Instant.now()
        ));

        assertThat(withdraw(fixture.user().getUserId())).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(appUserRepository.findById(fixture.user().getUserId())).isPresent();
        assertThat(capacityHoldRepository.findById(fixture.activeHold().getHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE));
        assertThat(refundRepository.findAll()).singleElement()
            .extracting(refund -> refund.getStatus())
            .isEqualTo(RefundStatus.REQUESTED);
    }

    @ParameterizedTest
    @EnumSource(value = OperatorApplicationStatus.class, names = {"APPROVED", "REJECTED"})
    void withdraw_afterReviewerRoleRevocation_unlinksInspectorAndPreservesReviewResult(
        OperatorApplicationStatus applicationStatus
    ) throws Exception {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser reviewer = saveUser("reviewer-" + suffix + "@example.com");
        UserRoleAssignment reviewerRole = userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            reviewer,
            UserRole.REGION_ADMIN,
            region
        ));
        AppUser applicant = saveUser("applicant-" + suffix + "@example.com");
        String rejectedReason = applicationStatus == OperatorApplicationStatus.REJECTED ? "사업자 정보 미비" : null;
        OperatorApplication application = operatorApplicationRepository.saveAndFlush(new OperatorApplication(
            applicant,
            region,
            "사업자 정보",
            applicationStatus,
            reviewer,
            rejectedReason
        ));
        Instant reviewedAt = findReviewTime(application.getOperatorApplicationId());
        reviewerRole.revoke(Instant.now(), "ROLE_REVOKED");
        userRoleAssignmentRepository.saveAndFlush(reviewerRole);

        mockMvc.perform(delete(WITHDRAWAL_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(reviewer.getUserId())))
            .andExpect(status().isOk());

        assertThat(appUserRepository.findById(reviewer.getUserId())).isEmpty();
        assertThat(operatorApplicationRepository.findById(application.getOperatorApplicationId()))
            .hasValueSatisfying(savedApplication -> {
                assertThat(savedApplication.getStatus()).isEqualTo(applicationStatus);
                assertThat(savedApplication.getInspectedUser()).isNull();
                assertThat(savedApplication.getRejectedReason()).isEqualTo(rejectedReason);
            });
        assertThat(findReviewTime(application.getOperatorApplicationId())).isEqualTo(reviewedAt);
    }

    @Test
    @Timeout(10)
    void withdraw_whenConcurrentRequestWaits_returnsUnauthenticatedAfterFirstRequestDeletesAccount() throws Exception {
        AppUser user = saveUser("visitor-" + Long.toUnsignedString(System.nanoTime()) + "@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.VISITOR, null));
        CountDownLatch firstRequestEnteredRedis = new CountDownLatch(1);
        CountDownLatch releaseFirstRequest = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstRequestEnteredRedis.countDown();
            await(releaseFirstRequest);
            return null;
        }).when(refreshTokenStore).revokeAllFamilies(anyLong());

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<?> firstRequest = executorService.submit(() -> withdrawUserUseCase.withdraw(user.getUserId()));
            assertThat(firstRequestEnteredRedis.await(3, TimeUnit.SECONDS)).isTrue();

            Future<ErrorCode> secondRequest = executorService.submit(() -> withdraw(user.getUserId()));
            assertThat(secondRequest.isDone()).isFalse();

            releaseFirstRequest.countDown();
            firstRequest.get(3, TimeUnit.SECONDS);
            assertThat(secondRequest.get(3, TimeUnit.SECONDS)).isEqualTo(ErrorCode.UNAUTHENTICATED);
        }
    }

    @Test
    @Timeout(10)
    void withdraw_blocksReservationHoldCreationUntilAccountDeletion() throws Exception {
        Fixture fixture = createFixture();

        assertUserCommandWaitsForWithdrawalAndIsRejected(
            fixture.user().getUserId(),
            () -> createReservationHoldUseCase.create(
                fixture.user().getUserId(),
                new CreateReservationHoldRequest(fixture.session().getSessionId().toString(), 1)
            )
        );
    }

    @Test
    @Timeout(10)
    void withdraw_blocksReservationConfirmationUntilAccountDeletion() throws Exception {
        Fixture fixture = createFixture();

        assertUserCommandWaitsForWithdrawalAndIsRejected(
            fixture.user().getUserId(),
            () -> reservationConfirmationUseCase.confirm(
                fixture.user().getUserId(),
                fixture.activeHold().getHoldId().toString(),
                "withdrawal-confirm-" + fixture.user().getUserId(),
                UUID.randomUUID()
            )
        );
    }

    @Test
    @Timeout(10)
    void withdraw_blocksReservationCancellationUntilAccountDeletion() throws Exception {
        Fixture fixture = createFixture();

        assertUserCommandWaitsForWithdrawalAndIsRejected(
            fixture.user().getUserId(),
            () -> reservationCancellationUseCase.cancel(
                fixture.user().getUserId(),
                fixture.reservation().getReservationId(),
                UUID.randomUUID()
            )
        );
    }

    @Test
    @Timeout(10)
    void withdraw_blocksReservationQrIssuanceUntilAccountDeletion() throws Exception {
        Fixture fixture = createFixture();

        assertUserCommandWaitsForWithdrawalAndIsRejected(
            fixture.user().getUserId(),
            () -> getMyReservationQrUseCase.get(
                fixture.user().getUserId(),
                fixture.reservation().getReservationId()
            )
        );
    }

    private CouponWithdrawalFixture createCouponWithdrawalFixture(Fixture fixture) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            CouponPolicy couponPolicy = createPublishedCouponPolicy(fixture, now);
            Coupon availableCoupon = couponRepository.saveAndFlush(new Coupon(
                couponPolicy,
                fixture.user(),
                now.minusSeconds(60),
                now.plusSeconds(86_400)
            ));
            Coupon reservedCoupon = new Coupon(
                couponPolicy,
                fixture.user(),
                now.minusSeconds(60),
                now.plusSeconds(86_400)
            );
            reservedCoupon.reserve();
            reservedCoupon = couponRepository.saveAndFlush(reservedCoupon);
            Coupon usedCoupon = new Coupon(
                couponPolicy,
                fixture.user(),
                now.minusSeconds(60),
                now.plusSeconds(86_400)
            );
            usedCoupon.reserve();
            usedCoupon.use();
            usedCoupon = couponRepository.saveAndFlush(usedCoupon);
            couponStatusHistoryRepository.saveAndFlush(new CouponStatusHistory(
                usedCoupon,
                CouponStatus.AVAILABLE,
                CouponStatus.RESERVED,
                "COUPON_RESERVED",
                "USER",
                now.minusSeconds(45)
            ));
            couponStatusHistoryRepository.saveAndFlush(new CouponStatusHistory(
                usedCoupon,
                CouponStatus.RESERVED,
                CouponStatus.USED,
                "COUPON_USED",
                "USER",
                now.minusSeconds(30)
            ));
            Coupon expiredCoupon = couponRepository.saveAndFlush(new Coupon(
                couponPolicy,
                fixture.user(),
                now.minusSeconds(120),
                now.minusSeconds(60)
            ));
            jdbcTemplate.update(
                "UPDATE coupon SET status = 'EXPIRED' WHERE coupon_id = ?",
                expiredCoupon.getCouponId()
            );
            couponStatusHistoryRepository.saveAndFlush(new CouponStatusHistory(
                expiredCoupon,
                CouponStatus.AVAILABLE,
                CouponStatus.EXPIRED,
                "EXPIRATION_SCHEDULE",
                "SYSTEM",
                now
            ));
            Coupon invalidatedCoupon = new Coupon(
                couponPolicy,
                fixture.user(),
                now.minusSeconds(60),
                now.plusSeconds(86_400)
            );
            invalidatedCoupon.invalidate();
            invalidatedCoupon = couponRepository.saveAndFlush(invalidatedCoupon);
            couponStatusHistoryRepository.saveAndFlush(new CouponStatusHistory(
                invalidatedCoupon,
                CouponStatus.AVAILABLE,
                CouponStatus.INVALIDATED,
                "USER_WITHDRAWAL",
                "USER",
                now
            ));
            Visit visit = visitRepository.saveAndFlush(new Visit(
                fixture.session().getRegion(),
                fixture.reservation(),
                fixture.user(),
                fixture.session().getContent(),
                fixture.session(),
                fixture.session().getContent().getOperator(),
                CheckinMethod.QR,
                now
            ));
            CouponIssuance couponIssuance = couponIssuanceRepository.saveAndFlush(new CouponIssuance(
                availableCoupon,
                couponPolicy,
                fixture.user(),
                visit,
                null,
                null,
                "withdrawal-coupon-issuance-" + Long.toUnsignedString(System.nanoTime()),
                now
            ));
            CouponIssuance usedCouponIssuance = couponIssuanceRepository.saveAndFlush(new CouponIssuance(
                usedCoupon,
                couponPolicy,
                fixture.user(),
                visit,
                null,
                null,
                "withdrawal-used-coupon-issuance-" + Long.toUnsignedString(System.nanoTime()),
                now
            ));
            return new CouponWithdrawalFixture(
                availableCoupon.getCouponId(),
                reservedCoupon.getCouponId(),
                usedCoupon.getCouponId(),
                expiredCoupon.getCouponId(),
                invalidatedCoupon.getCouponId(),
                couponIssuance.getCouponIssuanceId(),
                usedCouponIssuance.getCouponIssuanceId()
            );
        });
    }

    private Long createExpiredAvailableCoupon(Fixture fixture) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            Coupon coupon = couponRepository.saveAndFlush(new Coupon(
                createPublishedCouponPolicy(fixture, now),
                fixture.user(),
                now.minusSeconds(120),
                now.minusSeconds(60)
            ));
            return coupon.getCouponId();
        });
    }

    private CouponPolicy createPublishedCouponPolicy(
        Fixture fixture,
        Instant now
    ) {
        CouponPolicy couponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
            fixture.session().getContent(),
            fixture.session().getRegion(),
            "탈퇴 테스트 쿠폰",
            null,
            CouponIssuanceType.VISIT,
            1_000L,
            1_000L,
            30,
            now.minusSeconds(3_600),
            now.plusSeconds(3_600),
            10L
        ));
        couponPolicy.publish(now);
        return couponPolicyRepository.saveAndFlush(couponPolicy);
    }

    private void assertCouponStatusAndUnlinkedUser(
        Long couponId,
        CouponStatus expectedStatus
    ) {
        assertThat(couponRepository.findById(couponId))
            .hasValueSatisfying(coupon -> {
                assertThat(coupon.getStatus()).isEqualTo(expectedStatus);
                assertThat(coupon.getUser()).isNull();
            });
    }

    private void assertCouponInvalidationHistory(
        Long couponId,
        CouponStatus previousStatus
    ) {
        assertThat(couponStatusHistoryRepository.findAllByCouponCouponIdOrderByOccurredAtAsc(couponId))
            .singleElement()
            .satisfies(history -> {
                assertThat(history.getPreviousStatus()).isEqualTo(previousStatus);
                assertThat(history.getNextStatus()).isEqualTo(CouponStatus.INVALIDATED);
                assertThat(history.getReasonCode()).isEqualTo("USER_WITHDRAWAL");
                assertThat(history.getActorKind()).isEqualTo("USER");
            });
    }

    private void assertPreservedTerminalCouponHistories(CouponWithdrawalFixture couponFixture) {
        List<CouponStatusHistory> usedHistories = couponStatusHistoryRepository
            .findAllByCouponCouponIdOrderByOccurredAtAsc(couponFixture.usedCouponId());
        assertThat(usedHistories).hasSize(2);
        assertCouponStatusHistory(
            usedHistories.get(0),
            CouponStatus.AVAILABLE,
            CouponStatus.RESERVED,
            "COUPON_RESERVED",
            "USER"
        );
        assertCouponStatusHistory(
            usedHistories.get(1),
            CouponStatus.RESERVED,
            CouponStatus.USED,
            "COUPON_USED",
            "USER"
        );
        assertThat(couponStatusHistoryRepository.findAllByCouponCouponIdOrderByOccurredAtAsc(couponFixture.expiredCouponId()))
            .singleElement()
            .satisfies(history -> assertCouponStatusHistory(
                history, CouponStatus.AVAILABLE, CouponStatus.EXPIRED, "EXPIRATION_SCHEDULE", "SYSTEM"
            ));
        assertThat(couponStatusHistoryRepository.findAllByCouponCouponIdOrderByOccurredAtAsc(couponFixture.invalidatedCouponId()))
            .singleElement()
            .satisfies(history -> assertCouponStatusHistory(
                history, CouponStatus.AVAILABLE, CouponStatus.INVALIDATED, "USER_WITHDRAWAL", "USER"
            ));
    }

    private void assertCouponStatusHistory(
        CouponStatusHistory history,
        CouponStatus previousStatus,
        CouponStatus nextStatus,
        String reasonCode,
        String actorKind
    ) {
        assertThat(history.getPreviousStatus()).isEqualTo(previousStatus);
        assertThat(history.getNextStatus()).isEqualTo(nextStatus);
        assertThat(history.getReasonCode()).isEqualTo(reasonCode);
        assertThat(history.getActorKind()).isEqualTo(actorKind);
    }

    private Fixture createFixture() {
        Instant now = Instant.now();
        String suffix = Long.toUnsignedString(System.nanoTime());
        AppUser user = saveUser("visitor-" + suffix + "@example.com");
        AppUser owner = saveUser("owner-" + suffix + "@example.com");
        AppUser reviewer = saveUser("reviewer-" + suffix + "@example.com");
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.VISITOR, null));

        Content content = contentRepository.saveAndFlush(new Content(
            region,
            owner,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 행사",
            "행사 설명",
            "김해시",
            "10:00-18:00",
            "01012345678",
            "주의사항",
            "전체 이용가",
            "준비물 없음",
            "취소 정책",
            now
        ));
        ContentSession session = new ContentSession(
            content,
            region,
            now.plusSeconds(86_400),
            now.plusSeconds(90_000),
            now.plusSeconds(85_200),
            now.plusSeconds(89_000),
            10
        );
        session.approve(reviewer, now);
        session = contentSessionRepository.saveAndFlush(session);

        CapacityHold activeHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            user,
            1,
            CapacityHoldStatus.ACTIVE,
            now.plusSeconds(3_600),
            null,
            null,
            null,
            now
        ));
        CapacityHold consumedHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            user,
            1,
            CapacityHoldStatus.CONSUMED,
            now.plusSeconds(3_600),
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
            user,
            ReservationStatus.CONFIRMED,
            now,
            null,
            null,
            null,
            null
        ));
        jdbcTemplate.update("UPDATE content_session SET remaining_capacity = 8 WHERE session_id = ?", session.getSessionId());
        return new Fixture(user, session, activeHold, reservation);
    }

    private Payment createApprovedPayment(Fixture fixture) {
        CapacityHold consumedHold = fixture.reservation().getCapacityHold();
        ReservationPriceSnapshot snapshot = reservationPriceSnapshotRepository.saveAndFlush(
            new ReservationPriceSnapshot(consumedHold, null, 20_000, 0, 20_000, "KRW", Instant.now())
        );
        Payment payment = new Payment(
            consumedHold,
            snapshot,
            "withdrawal-approved-payment-" + System.nanoTime(),
            Instant.now()
        );
        payment.approve(fixture.reservation(), "portone-" + System.nanoTime(), Instant.now());
        return paymentRepository.saveAndFlush(payment);
    }

    private Refund createTerminalRefund(
        Payment payment,
        RefundStatus refundStatus
    ) {
        Instant now = Instant.now();
        Refund refund = new Refund(payment, 20_000, now);
        refund.startProcessing();
        switch (refundStatus) {
            case SUCCEEDED -> refund.succeed(now);
            case FAILED -> refund.fail(now);
            case DISCREPANT -> refund.markDiscrepant(now);
            default -> throw new IllegalArgumentException("terminal refund status is required");
        }
        return refundRepository.saveAndFlush(refund);
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "password-hash",
            "홍길동",
            "01012345678",
            AppUserStatus.ACTIVE
        ));
    }

    private void expireActiveHold(Fixture fixture) {
        jdbcTemplate.update(
            "UPDATE capacity_hold SET expires_at = CURRENT_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE hold_id = ?",
            fixture.activeHold().getHoldId()
        );
    }

    private ErrorCode withdraw(Long userId) {
        try {
            withdrawUserUseCase.withdraw(userId);
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private Instant findReviewTime(Long operatorApplicationId) {
        return jdbcTemplate.queryForObject(
            "SELECT updated_at FROM operator_application WHERE operator_application_id = ?",
            Timestamp.class,
            operatorApplicationId
        ).toInstant();
    }

    private void assertUserCommandWaitsForWithdrawalAndIsRejected(
        Long userId,
        Runnable userCommand
    ) throws Exception {
        CountDownLatch withdrawalEnteredRedis = new CountDownLatch(1);
        CountDownLatch releaseWithdrawal = new CountDownLatch(1);
        CountDownLatch commandStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            withdrawalEnteredRedis.countDown();
            await(releaseWithdrawal);
            return null;
        }).when(refreshTokenStore).revokeAllFamilies(userId);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<?> withdrawal = executorService.submit(() -> withdrawUserUseCase.withdraw(userId));
            assertThat(withdrawalEnteredRedis.await(3, TimeUnit.SECONDS)).isTrue();

            Future<ErrorCode> command = executorService.submit(() -> {
                commandStarted.countDown();
                return execute(userCommand);
            });
            assertThat(commandStarted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(command.isDone()).isFalse();

            releaseWithdrawal.countDown();
            withdrawal.get(3, TimeUnit.SECONDS);
            assertThat(command.get(3, TimeUnit.SECONDS)).isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    private ErrorCode execute(Runnable userCommand) {
        try {
            userCommand.run();
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("withdrawal synchronization timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("withdrawal synchronization interrupted", exception);
        }
    }

    private record Fixture(
        AppUser user,
        ContentSession session,
        CapacityHold activeHold,
        Reservation reservation
    ) {
    }

    private record CouponWithdrawalFixture(
        Long availableCouponId,
        Long reservedCouponId,
        Long usedCouponId,
        Long expiredCouponId,
        Long invalidatedCouponId,
        Long couponIssuanceId,
        Long usedCouponIssuanceId
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class WithdrawalMySqlTestConfiguration {

        @Bean
        @Primary
        RefreshTokenStore refreshTokenStore() {
            return mock(RefreshTokenStore.class);
        }

        @Bean
        @Primary
        FailingWithdrawalCapacityHoldService failingWithdrawalCapacityHoldService(
            CapacityHoldRepository capacityHoldRepository
        ) {
            return new FailingWithdrawalCapacityHoldService(capacityHoldRepository);
        }
    }

    static class FailingWithdrawalCapacityHoldService extends CapacityHoldService {

        private boolean failAfterWithdrawalTermination;

        FailingWithdrawalCapacityHoldService(CapacityHoldRepository capacityHoldRepository) {
            super(capacityHoldRepository);
        }

        @Override
        public List<TerminatedCapacityHold> invalidateActiveHoldsForWithdrawal(Long userId) {
            List<TerminatedCapacityHold> terminatedCapacityHolds = super.invalidateActiveHoldsForWithdrawal(userId);
            if (failAfterWithdrawalTermination) {
                throw new IllegalStateException("simulated withdrawal capacity hold termination failure");
            }
            return terminatedCapacityHolds;
        }

        void failAfterNextWithdrawalTermination() {
            failAfterWithdrawalTermination = true;
        }

        void reset() {
            failAfterWithdrawalTermination = false;
        }
    }
}
