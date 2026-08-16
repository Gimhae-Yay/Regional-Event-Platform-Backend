package io.regionevent.regioneventbackend.domain.user.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActorLinkService;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponIssuanceService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyService;
import io.regionevent.regioneventbackend.domain.operator.service.OperatorApplicationService;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.payment.service.PaymentService;
import io.regionevent.regioneventbackend.domain.payment.service.RefundService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.domain.review.service.ReviewService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;

@Service
public class WithdrawUserUseCase {

    private static final String USER_WITHDRAWAL_REASON = "USER_WITHDRAWAL";
    private static final String USER_ACTOR_KIND = "USER";

    private final AppUserService appUserService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final ContentService contentService;
    private final RefreshTokenService refreshTokenService;
    private final CouponService couponService;
    private final CouponIssuanceService couponIssuanceService;
    private final CouponStatusHistoryService couponStatusHistoryService;
    private final CapacityHoldService capacityHoldService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final ReservationService reservationService;
    private final OperatorApplicationService operatorApplicationService;
    private final VisitService visitService;
    private final ReviewService reviewService;
    private final IdempotencyService idempotencyService;
    private final AuditEventActorLinkService auditEventActorLinkService;
    private final Clock clock;

    public WithdrawUserUseCase(
        AppUserService appUserService,
        UserRoleAssignmentService userRoleAssignmentService,
        ContentService contentService,
        RefreshTokenService refreshTokenService,
        CouponService couponService,
        CouponIssuanceService couponIssuanceService,
        CouponStatusHistoryService couponStatusHistoryService,
        CapacityHoldService capacityHoldService,
        PaymentService paymentService,
        RefundService refundService,
        ReservationService reservationService,
        OperatorApplicationService operatorApplicationService,
        VisitService visitService,
        ReviewService reviewService,
        IdempotencyService idempotencyService,
        AuditEventActorLinkService auditEventActorLinkService,
        Clock clock
    ) {
        this.appUserService = appUserService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.contentService = contentService;
        this.refreshTokenService = refreshTokenService;
        this.couponService = couponService;
        this.couponIssuanceService = couponIssuanceService;
        this.couponStatusHistoryService = couponStatusHistoryService;
        this.capacityHoldService = capacityHoldService;
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.reservationService = reservationService;
        this.operatorApplicationService = operatorApplicationService;
        this.visitService = visitService;
        this.reviewService = reviewService;
        this.idempotencyService = idempotencyService;
        this.auditEventActorLinkService = auditEventActorLinkService;
        this.clock = clock;
    }

    @Transactional
    public void withdraw(Long userId) {
        AppUser user = appUserService.findActiveUserForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        validateWithdrawable(userId);
        appUserService.startWithdrawal(user);

        refreshTokenService.revokeAllFamilies(userId);
        invalidateActiveCoupons(userId);
        couponIssuanceService.unlinkRecipientUserByUserId(userId);
        couponService.unlinkUserByUserId(userId);
        capacityHoldService.invalidateActiveHoldsForWithdrawal(userId);
        reservationService.cancelConfirmedReservationsForWithdrawal(userId);
        operatorApplicationService.cancelAndUnlinkByApplicantUserId(userId);
        operatorApplicationService.unlinkInspectorByUserId(userId);
        capacityHoldService.unlinkUserByUserId(userId);
        reservationService.unlinkUserByUserId(userId);
        visitService.unlinkAuthorByUserId(userId);
        reviewService.unlinkAuthorByUserId(userId);
        idempotencyService.unlinkActorByUserId(userId);
        auditEventActorLinkService.deleteByActorUserId(userId);
        userRoleAssignmentService.revokeAndUnlinkAllByUserId(userId, clock.instant());
        appUserService.delete(user);
    }

    private void invalidateActiveCoupons(Long userId) {
        Instant occurredAt = clock.instant();
        for (Coupon coupon : couponService.findActiveCouponsForWithdrawal(userId)) {
            couponStatusHistoryService.create(new CouponStatusHistory(
                coupon,
                coupon.invalidate(),
                CouponStatus.INVALIDATED,
                USER_WITHDRAWAL_REASON,
                USER_ACTOR_KIND,
                occurredAt
            ));
        }
    }

    private void validateWithdrawable(Long userId) {
        if (userRoleAssignmentService.hasPrivilegedRole(userId) || contentService.hasOwnedContent(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (paymentService.hasPendingPayment(userId)
            || paymentService.hasApprovedPaymentWithConfirmedReservationWithoutSucceededRefund(userId)
            || refundService.hasInProgressRefund(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
