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
import io.regionevent.regioneventbackend.domain.coupon.service.RestoreCouponUseCase;
import io.regionevent.regioneventbackend.domain.payment.dto.ResolveRefundFailureRequest;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ResolveRefundFailureUseCase {

    private static final String MANUAL_SUCCEEDED_REASON = "MANUAL_REFUND_SUCCEEDED";
    private static final String MANUAL_FAILED_REASON = "MANUAL_REFUND_FAILED";

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final RefundService refundService;
    private final RestoreCouponUseCase restoreCouponUseCase;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public ResolveRefundFailureUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        RefundService refundService,
        RestoreCouponUseCase restoreCouponUseCase,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.refundService = refundService;
        this.restoreCouponUseCase = restoreCouponUseCase;
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
            .requireAuthorizedPlatformAdminForUpdate(actorUserId);
        Refund refund = refundService.findByRefundIdForUpdate(refundId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        validateDiscrepant(refund);

        Instant resolvedAt = Instant.now(clock);
        if (confirmedStatus == RefundStatus.SUCCEEDED) {
            refund.resolveAsSucceeded(resolvedAt);
            restoreCouponUseCase.restoreForRefund(refund, requestId, new AuditEventActor(assignment));
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
