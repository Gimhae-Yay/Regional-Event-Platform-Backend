package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;

public record OperatorReservationPaymentInfo(
    Long reservationId,
    String reservationNo,
    Long contentId,
    Long sessionId,
    PaymentInfo payment,
    RefundInfo refund,
    Instant updatedAt
) {

    public record PaymentInfo(
        Long paymentId,
        PaymentStatus status,
        long finalAmount,
        String currency,
        DiscrepancyInfo discrepancy
    ) {
    }

    public record DiscrepancyInfo(
        Long discrepancyId,
        String status
    ) {
    }

    public record RefundInfo(
        Long refundId,
        RefundStatus status,
        long amount,
        Instant requestedAt,
        Instant completedAt
    ) {
    }
}
