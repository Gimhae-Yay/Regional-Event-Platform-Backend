package io.regionevent.regioneventbackend.domain.user.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActorLinkService;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyService;
import io.regionevent.regioneventbackend.domain.operator.service.OperatorApplicationService;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.payment.service.ExpirePendingPaymentForTerminatedHoldUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.domain.review.service.ReviewService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;

@Service
public class WithdrawUserUseCase {

    private final AppUserService appUserService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final ContentService contentService;
    private final RefreshTokenService refreshTokenService;
    private final CapacityHoldService capacityHoldService;
    private final ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase;
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
        CapacityHoldService capacityHoldService,
        ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase,
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
        this.capacityHoldService = capacityHoldService;
        this.expirePendingPaymentForTerminatedHoldUseCase = expirePendingPaymentForTerminatedHoldUseCase;
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
        UUID requestId = UUID.randomUUID();
        capacityHoldService.invalidateActiveHoldsForWithdrawal(userId)
            .forEach(capacityHold -> expirePendingPaymentForTerminatedHoldUseCase.expire(
                capacityHold,
                requestId,
                null
            ));
        reservationService.cancelConfirmedReservationsForWithdrawal(userId);
        operatorApplicationService.cancelAndUnlinkByApplicantUserId(userId);
        capacityHoldService.unlinkUserByUserId(userId);
        reservationService.unlinkUserByUserId(userId);
        visitService.unlinkAuthorByUserId(userId);
        reviewService.unlinkAuthorByUserId(userId);
        idempotencyService.unlinkActorByUserId(userId);
        auditEventActorLinkService.deleteByActorUserId(userId);
        userRoleAssignmentService.revokeAndUnlinkAllByUserId(userId, clock.instant());
        appUserService.delete(user);
    }

    private void validateWithdrawable(Long userId) {
        if (userRoleAssignmentService.hasPrivilegedRole(userId) || contentService.hasOwnedContent(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
