package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemption;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemptionStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponRedemptionService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.payment.dto.ResolveRefundFailureRequest;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ResolveRefundFailureUseCase {

    private static final String MANUAL_SUCCEEDED_REASON = "MANUAL_REFUND_SUCCEEDED";
    private static final String MANUAL_FAILED_REASON = "MANUAL_REFUND_FAILED";
    private static final String COUPON_RESTORE_REASON = "REFUND_SUCCEEDED";

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final RefundService refundService;
    private final CouponService couponService;
    private final CouponRedemptionService couponRedemptionService;
    private final CouponStatusHistoryService couponStatusHistoryService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public ResolveRefundFailureUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        RefundService refundService,
        CouponService couponService,
        CouponRedemptionService couponRedemptionService,
        CouponStatusHistoryService couponStatusHistoryService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.refundService = refundService;
        this.couponService = couponService;
        this.couponRedemptionService = couponRedemptionService;
        this.couponStatusHistoryService = couponStatusHistoryService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public ResolveRefundFailureResult resolve(
        Long actorUserId,
        Long refundId,
        ResolveRefundFailureRequest request,
        UUID requestId
    ) {
        RefundStatus confirmedStatus = validateConfirmedStatus(request == null ? null : request.confirmedStatus());
        String evidenceReference = normalizeRequired(request == null ? null : request.evidenceReference());
        String reason = normalizeRequired(request == null ? null : request.reason());
        PlatformAdminAssignment assignment = platformAdminAuthorizationService
            .requireAuthorizedPlatformAdmin(actorUserId);
        Refund refund = refundService.findByRefundIdForUpdate(refundId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        validateDiscrepant(refund);

        Instant resolvedAt = Instant.now(clock);
        if (confirmedStatus == RefundStatus.SUCCEEDED) {
            refund.resolveAsSucceeded(resolvedAt);
            restoreCouponIfEligible(refund, requestId, assignment);
        } else {
            refund.resolveAsFailed(resolvedAt);
        }
        recordRefundAudit(refund, requestId, assignment, reason, evidenceReference, resolvedAt);
        return new ResolveRefundFailureResult(refund.getRefundId(), refund.getStatus().name(), resolvedAt);
    }

    private RefundStatus validateConfirmedStatus(String confirmedStatus) {
        if (!RefundStatus.SUCCEEDED.name().equals(confirmedStatus)
            && !RefundStatus.FAILED.name().equals(confirmedStatus)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return RefundStatus.valueOf(confirmedStatus);
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private void validateDiscrepant(Refund refund) {
        if (refund.getStatus() != RefundStatus.DISCREPANT) {
            throw new BusinessException(ErrorCode.REFUND_STATE_CONFLICT);
        }
    }

    private void restoreCouponIfEligible(
        Refund refund,
        UUID requestId,
        PlatformAdminAssignment assignment
    ) {
        Reservation reservation = refund.getPayment().getReservation();
        if (reservation == null
            || reservation.getStatus() != ReservationStatus.CANCELLED
            || !reservation.getCancelledAt().isBefore(reservation.getContentSession().getStartsAt())
            || refund.getPayment().getReservationPriceSnapshot().getCoupon() == null) {
            return;
        }
        CouponRedemption redemption = couponRedemptionService.findByReservationPriceSnapshotIdForUpdate(
            refund.getPayment().getReservationPriceSnapshot().getReservationPriceSnapshotId()
        ).orElse(null);
        if (redemption == null || redemption.getStatus() == CouponRedemptionStatus.REVERSED) {
            return;
        }
        Coupon snapshotCoupon = refund.getPayment().getReservationPriceSnapshot().getCoupon();
        Coupon coupon = couponService.findByCouponIdForUpdate(snapshotCoupon.getCouponId())
            .orElseThrow(() -> new IllegalStateException("redemption coupon does not exist"));
        if (!redemption.getCoupon().getCouponId().equals(coupon.getCouponId())
            || !redemption.getReservation().getReservationId().equals(reservation.getReservationId())
            || !redemption.getReservationPriceSnapshot().getReservationPriceSnapshotId().equals(
                refund.getPayment().getReservationPriceSnapshot().getReservationPriceSnapshotId()
            )
            || coupon.getStatus() != CouponStatus.USED) {
            return;
        }
        Instant restoredAt = couponService.findCurrentDatabaseTime();
        redemption.reverse(restoredAt);
        CouponStatus restoredStatus = couponService.restoreUsedCoupon(coupon, restoredAt);
        couponStatusHistoryService.create(new CouponStatusHistory(
            coupon,
            CouponStatus.USED,
            restoredStatus,
            COUPON_RESTORE_REASON,
            assignment.getGrade().name(),
            restoredAt
        ));
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            refund.getPayment().getCapacityHold().getRegion(),
            AuditEventTargetType.COUPON,
            coupon.getCouponId(),
            CouponStatus.USED.name(),
            restoredStatus.name(),
            AuditEventResult.SUCCESS,
            COUPON_RESTORE_REASON,
            null,
            null,
            new AuditEventActor(assignment),
            restoredAt
        ));
    }

    private void recordRefundAudit(
        Refund refund,
        UUID requestId,
        PlatformAdminAssignment assignment,
        String reason,
        String evidenceReference,
        Instant resolvedAt
    ) {
        String reasonCode = refund.getStatus() == RefundStatus.SUCCEEDED
            ? MANUAL_SUCCEEDED_REASON
            : MANUAL_FAILED_REASON;
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            refund.getPayment().getCapacityHold().getRegion(),
            AuditEventTargetType.REFUND,
            refund.getRefundId(),
            RefundStatus.DISCREPANT.name(),
            refund.getStatus().name(),
            AuditEventResult.SUCCESS,
            reasonCode,
            reason,
            evidenceReference,
            new AuditEventActor(assignment),
            resolvedAt
        ));
    }
}
