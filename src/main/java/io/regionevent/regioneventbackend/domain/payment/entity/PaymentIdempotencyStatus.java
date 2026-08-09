package io.regionevent.regioneventbackend.domain.payment.entity;

public enum PaymentIdempotencyStatus {

    PROCESSING,
    SUCCEEDED,
    FAILED
}
