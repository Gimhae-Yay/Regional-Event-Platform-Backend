package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.payment.dto.CreateRefundRequest;
import io.regionevent.regioneventbackend.domain.payment.dto.CreateRefundResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptInitiatorKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundFailureReasonCode;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneLookupException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreateRefundUseCase {

    private static final String CURRENCY = "KRW";
    private static final String DISCREPANCY_ACTION_TYPE = "FULL_REFUND_REQUEST";
    private static final String DISCREPANCY_ACTION_REASON_CODE = "MANUAL_FULL_REFUND";

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final RefundAttemptService refundAttemptService;
    private final PaymentDiscrepancyService paymentDiscrepancyService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final PortOnePaymentGateway portOnePaymentGateway;

    public CreateRefundUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        PaymentService paymentService,
        RefundService refundService,
        RefundAttemptService refundAttemptService,
        PaymentDiscrepancyService paymentDiscrepancyService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        PortOnePaymentGateway portOnePaymentGateway
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.refundAttemptService = refundAttemptService;
        this.paymentDiscrepancyService = paymentDiscrepancyService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.portOnePaymentGateway = portOnePaymentGateway;
    }

    @Transactional
    public CreateRefundResponse create(
        Long actorUserId,
        String paymentIdValue,
        CreateRefundRequest request,
        UUID requestId
    ) {
        PlatformAdminAssignment assignment = platformAdminAuthorizationService
            .requireAuthorizedPlatformAdmin(actorUserId);
        long paymentId = toPositiveId(paymentIdValue);
        String evidenceReference = normalizeRequired(request == null ? null : request.evidenceReference());
        String reason = normalizeRequired(request == null ? null : request.reason());
        Payment payment = paymentService.findByPaymentIdForUpdate(paymentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Refund existing = refundService.findByPaymentIdForUpdate(paymentId).orElse(null);
        if (existing != null) {
            return CreateRefundResponse.from(existing);
        }
        if (payment.getStatus() != PaymentStatus.APPROVED && payment.getStatus() != PaymentStatus.DISCREPANT) {
            throw new BusinessException(ErrorCode.REFUND_PAYMENT_CONFLICT);
        }
        if (payment.getPortonePaymentId() == null) {
            throw new BusinessException(ErrorCode.REFUND_PAYMENT_CONFLICT);
        }
        Instant now = Instant.now();
        Refund refund = refundService.create(new Refund(
            payment,
            payment.getReservationPriceSnapshot().getFinalAmount(),
            now
        ));
        refund.startProcessing();
        RefundAttempt attempt = refundAttemptService.create(new RefundAttempt(
            refund,
            1,
            toInitiatorKind(assignment),
            now
        ));
        requestDiscrepancyRefund(payment, evidenceReference, reason, now);
        try {
            PortOnePaymentGateway.PortOneCancellation cancellation = portOnePaymentGateway.cancelPayment(
                payment.getPortonePaymentId(),
                refund.getAmount(),
                reason
            );
            attempt.respond(cancellation.cancellationId(), cancellation.status(), cancellation.resultHash());
            if (cancellation.isSucceeded()) {
                refund.succeed(Instant.now());
            } else {
                refund.fail(Instant.now());
            }
        } catch (PortOneLookupException exception) {
            attempt.noResponse(RefundFailureReasonCode.UNKNOWN);
            refund.markDiscrepant(Instant.now());
        }
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            payment.getCapacityHold().getRegion(),
            AuditEventTargetType.REFUND,
            refund.getRefundId(),
            "REQUESTED",
            refund.getStatus().name(),
            AuditEventResult.SUCCESS,
            null,
            reason,
            evidenceReference,
            new AuditEventActor(assignment),
            Instant.now()
        ));
        return CreateRefundResponse.from(refund);
    }

    private void requestDiscrepancyRefund(
        Payment payment,
        String evidenceReference,
        String reason,
        Instant actedAt
    ) {
        PaymentDiscrepancy discrepancy = paymentDiscrepancyService
            .findByPaymentIdForUpdate(payment.getPaymentId())
            .orElse(null);
        if (discrepancy == null || !"OPEN".equals(discrepancy.getStatus())) {
            return;
        }
        discrepancy.requestRefund();
        paymentDiscrepancyService.createAction(
            discrepancy,
            DISCREPANCY_ACTION_TYPE,
            evidenceReference,
            DISCREPANCY_ACTION_REASON_CODE,
            CURRENCY,
            actedAt
        );
    }

    private long toPositiveId(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!value.matches("[0-9]+")) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        try {
            long id = Long.parseLong(value);
            if (id < 1) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
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

    private RefundAttemptInitiatorKind toInitiatorKind(PlatformAdminAssignment assignment) {
        return RefundAttemptInitiatorKind.valueOf(assignment.getGrade().name());
    }
}
