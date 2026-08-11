package io.regionevent.regioneventbackend.domain.payment.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.payment.service.PaymentDiscrepancyListInfo;

public record GetPaymentDiscrepanciesResponse(List<DiscrepancyResponse> discrepancies) {

    public GetPaymentDiscrepanciesResponse {
        discrepancies = List.copyOf(discrepancies);
    }

    public static GetPaymentDiscrepanciesResponse from(List<PaymentDiscrepancyListInfo> discrepancies) {
        return new GetPaymentDiscrepanciesResponse(discrepancies.stream()
            .map(DiscrepancyResponse::from)
            .toList());
    }

    public record DiscrepancyResponse(
        String discrepancyId,
        String paymentId,
        String discrepancyType,
        String status,
        long finalAmount,
        String currency,
        Instant detectedAt
    ) {

        private static DiscrepancyResponse from(PaymentDiscrepancyListInfo discrepancy) {
            return new DiscrepancyResponse(
                discrepancy.discrepancyId().toString(),
                discrepancy.paymentId().toString(),
                discrepancy.discrepancyType(),
                discrepancy.status(),
                discrepancy.finalAmount(),
                discrepancy.currency(),
                discrepancy.detectedAt()
            );
        }
    }
}
