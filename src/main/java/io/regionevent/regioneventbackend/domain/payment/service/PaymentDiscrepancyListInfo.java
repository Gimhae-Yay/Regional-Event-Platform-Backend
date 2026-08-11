package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;

public record PaymentDiscrepancyListInfo(
    Long discrepancyId,
    Long paymentId,
    String discrepancyType,
    String status,
    long finalAmount,
    String currency,
    Instant detectedAt
) {

    public static PaymentDiscrepancyListInfo from(PaymentDiscrepancy discrepancy) {
        ReservationPriceSnapshot snapshot = discrepancy.getPayment().getReservationPriceSnapshot();
        return new PaymentDiscrepancyListInfo(
            discrepancy.getPaymentDiscrepancyId(),
            discrepancy.getPayment().getPaymentId(),
            discrepancy.getDiscrepancyType(),
            discrepancy.getStatus(),
            snapshot.getFinalAmount(),
            snapshot.getCurrency(),
            discrepancy.getDetectedAt()
        );
    }
}
