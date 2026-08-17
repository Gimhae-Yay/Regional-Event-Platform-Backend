package io.regionevent.regioneventbackend.domain.payment.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.payment.entity.Refund;

public record CreateRefundResponse(
    String refundId,
    String paymentId,
    long amount,
    String currency,
    String status,
    Instant requestedAt
) {

    private static final String CURRENCY = "KRW";

    public static CreateRefundResponse from(Refund refund) {
        return new CreateRefundResponse(
            String.valueOf(refund.getRefundId()),
            String.valueOf(refund.getPayment().getPaymentId()),
            refund.getAmount(),
            CURRENCY,
            refund.getStatus().name(),
            refund.getRequestedAt()
        );
    }
}
