package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancyAction;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentVerification;

public record PaymentDiscrepancyDetailInfo(
    Long discrepancyId,
    String discrepancyType,
    String status,
    Instant detectedAt,
    Long paymentId,
    Long holdId,
    String orderId,
    String portonePaymentId,
    String paymentStatus,
    long finalAmount,
    String currency,
    List<VerificationInfo> verifications,
    List<ActionInfo> actions
) {

    public PaymentDiscrepancyDetailInfo {
        verifications = List.copyOf(verifications);
        actions = List.copyOf(actions);
    }

    public static PaymentDiscrepancyDetailInfo from(
        PaymentDiscrepancy discrepancy,
        List<PaymentVerification> verifications,
        List<PaymentDiscrepancyAction> actions
    ) {
        Payment payment = discrepancy.getPayment();
        if (payment.getStatus() != PaymentStatus.DISCREPANT) {
            throw new IllegalStateException("payment discrepancy must reference a discrepant payment");
        }
        return new PaymentDiscrepancyDetailInfo(
            discrepancy.getPaymentDiscrepancyId(),
            discrepancy.getDiscrepancyType(),
            discrepancy.getStatus(),
            discrepancy.getDetectedAt(),
            payment.getPaymentId(),
            payment.getCapacityHold().getHoldId(),
            payment.getOrderId(),
            payment.getPortonePaymentId(),
            payment.getStatus().name(),
            payment.getReservationPriceSnapshot().getFinalAmount(),
            payment.getReservationPriceSnapshot().getCurrency(),
            verifications.stream().map(VerificationInfo::from).toList(),
            actions.stream().map(ActionInfo::from).toList()
        );
    }

    public record VerificationInfo(
        Long paymentVerificationId,
        String reason,
        String externalStatus,
        long observedAmount,
        boolean matched,
        Instant verifiedAt
    ) {

        private static VerificationInfo from(PaymentVerification verification) {
            return new VerificationInfo(
                verification.getPaymentVerificationId(),
                verification.getVerificationReason(),
                verification.getExternalStatus(),
                verification.getObservedAmount(),
                "APPROVE".equals(verification.getInternalDecision()),
                verification.getVerifiedAt()
            );
        }
    }

    public record ActionInfo(
        Long actionId,
        String action,
        String evidenceReference,
        String reason,
        Instant actedAt
    ) {

        private static ActionInfo from(PaymentDiscrepancyAction action) {
            return new ActionInfo(
                action.getPaymentDiscrepancyActionId(),
                action.getActionType(),
                action.getEvidenceReference(),
                action.getReasonCode(),
                action.getActedAt()
            );
        }
    }
}
