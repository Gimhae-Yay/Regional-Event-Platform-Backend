package io.regionevent.regioneventbackend.domain.payment.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.payment.service.PaymentDiscrepancyDetailInfo;

public record GetPaymentDiscrepancyResponse(
    DiscrepancyResponse discrepancy,
    PaymentResponse payment,
    List<VerificationResponse> verifications,
    List<ActionResponse> actions
) {

    public GetPaymentDiscrepancyResponse {
        verifications = List.copyOf(verifications);
        actions = List.copyOf(actions);
    }

    public static GetPaymentDiscrepancyResponse from(PaymentDiscrepancyDetailInfo discrepancy) {
        return new GetPaymentDiscrepancyResponse(
            new DiscrepancyResponse(
                discrepancy.discrepancyId().toString(),
                discrepancy.discrepancyType(),
                discrepancy.status(),
                discrepancy.detectedAt()
            ),
            new PaymentResponse(
                discrepancy.paymentId().toString(),
                discrepancy.holdId().toString(),
                discrepancy.orderId(),
                discrepancy.portonePaymentId(),
                discrepancy.paymentStatus(),
                discrepancy.finalAmount(),
                discrepancy.currency()
            ),
            discrepancy.verifications().stream()
                .map(verification -> new VerificationResponse(
                    verification.paymentVerificationId().toString(),
                    verification.reason(),
                    verification.externalStatus(),
                    verification.observedAmount(),
                    verification.matched(),
                    verification.verifiedAt()
                ))
                .toList(),
            discrepancy.actions().stream()
                .map(action -> new ActionResponse(
                    action.actionId().toString(),
                    action.action(),
                    action.evidenceReference(),
                    action.reason(),
                    action.actedAt()
                ))
                .toList()
        );
    }

    public record DiscrepancyResponse(
        String discrepancyId,
        String discrepancyType,
        String status,
        Instant detectedAt
    ) {
    }

    public record PaymentResponse(
        String paymentId,
        String holdId,
        String orderId,
        String portonePaymentId,
        String status,
        long finalAmount,
        String currency
    ) {
    }

    public record VerificationResponse(
        String paymentVerificationId,
        String reason,
        String externalStatus,
        long observedAmount,
        boolean matched,
        Instant verifiedAt
    ) {
    }

    public record ActionResponse(
        String actionId,
        String action,
        String evidenceReference,
        String reason,
        Instant actedAt
    ) {
    }
}
