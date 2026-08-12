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
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.dto.ResolvePaymentDiscrepancyRequest;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ResolvePaymentDiscrepancyUseCase {

    private static final String OPEN_STATUS = "OPEN";
    private static final String RESOLVED_NO_ISSUE_STATUS = "RESOLVED_NO_ISSUE";
    private static final String ACTION_TYPE = "NO_ISSUE_CLOSE";
    private static final String REASON_CODE = "MANUAL_NO_ISSUE_CLOSE";

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final PaymentDiscrepancyService paymentDiscrepancyService;
    private final PaymentDiscrepancyActionService paymentDiscrepancyActionService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public ResolvePaymentDiscrepancyUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        PaymentDiscrepancyService paymentDiscrepancyService,
        PaymentDiscrepancyActionService paymentDiscrepancyActionService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.paymentDiscrepancyService = paymentDiscrepancyService;
        this.paymentDiscrepancyActionService = paymentDiscrepancyActionService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public ResolvePaymentDiscrepancyResult resolve(
        Long actorUserId,
        Long discrepancyId,
        ResolvePaymentDiscrepancyRequest request,
        UUID requestId
    ) {
        String evidenceReference = normalizeRequired(request == null ? null : request.evidenceReference());
        String reason = normalizeRequired(request == null ? null : request.reason());
        PlatformAdminAssignment assignment = platformAdminAuthorizationService
            .requireAuthorizedPlatformAdmin(actorUserId);
        PaymentDiscrepancy discrepancy = paymentDiscrepancyService.findByIdForUpdate(discrepancyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        validateOpen(discrepancy);

        Instant resolvedAt = Instant.now(clock);

        discrepancy.resolveNoIssue();
        paymentDiscrepancyActionService.create(
            discrepancy,
            ACTION_TYPE,
            evidenceReference,
            REASON_CODE,
            RESOLVED_NO_ISSUE_STATUS,
            resolvedAt
        );
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            discrepancy.getPayment().getCapacityHold().getRegion(),
            AuditEventTargetType.PAYMENT_DISCREPANCY,
            discrepancy.getPaymentDiscrepancyId(),
            OPEN_STATUS,
            RESOLVED_NO_ISSUE_STATUS,
            AuditEventResult.SUCCESS,
            REASON_CODE,
            reason,
            evidenceReference,
            new AuditEventActor(assignment),
            resolvedAt
        ));
        return new ResolvePaymentDiscrepancyResult(
            discrepancy.getPaymentDiscrepancyId(),
            RESOLVED_NO_ISSUE_STATUS,
            resolvedAt
        );
    }

    private void validateOpen(PaymentDiscrepancy discrepancy) {
        if (!OPEN_STATUS.equals(discrepancy.getStatus())) {
            throw new BusinessException(ErrorCode.PAYMENT_DISCREPANCY_STATE_CONFLICT);
        }
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
}
