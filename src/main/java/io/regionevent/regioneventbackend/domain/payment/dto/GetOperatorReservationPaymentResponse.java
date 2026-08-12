package io.regionevent.regioneventbackend.domain.payment.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.service.OperatorReservationPaymentInfo;

public record GetOperatorReservationPaymentResponse(
    String reservationId,
    String reservationNo,
    String contentId,
    String sessionId,
    PaymentResponse payment,
    RefundResponse refund,
    Instant updatedAt
) {

    public static GetOperatorReservationPaymentResponse from(OperatorReservationPaymentInfo paymentInfo) {
        return new GetOperatorReservationPaymentResponse(
            paymentInfo.reservationId().toString(),
            paymentInfo.reservationNo(),
            paymentInfo.contentId().toString(),
            paymentInfo.sessionId().toString(),
            toPaymentResponse(paymentInfo.payment()),
            toRefundResponse(paymentInfo.refund()),
            paymentInfo.updatedAt()
        );
    }

    private static PaymentResponse toPaymentResponse(OperatorReservationPaymentInfo.PaymentInfo payment) {
        if (payment == null) {
            return null;
        }
        return new PaymentResponse(
            payment.paymentId().toString(),
            payment.status(),
            payment.finalAmount(),
            payment.currency(),
            payment.discrepancy() == null ? null : new DiscrepancyResponse(
                payment.discrepancy().discrepancyId().toString(),
                payment.discrepancy().status()
            )
        );
    }

    private static RefundResponse toRefundResponse(OperatorReservationPaymentInfo.RefundInfo refund) {
        if (refund == null) {
            return null;
        }
        return new RefundResponse(
            refund.refundId().toString(),
            refund.status(),
            refund.amount(),
            refund.requestedAt(),
            refund.completedAt()
        );
    }

    public record PaymentResponse(
        String paymentId,
        PaymentStatus status,
        long finalAmount,
        String currency,
        DiscrepancyResponse discrepancy
    ) {
    }

    public record DiscrepancyResponse(
        String discrepancyId,
        String status
    ) {
    }

    public record RefundResponse(
        String refundId,
        RefundStatus status,
        long amount,
        Instant requestedAt,
        Instant completedAt
    ) {
    }
}
