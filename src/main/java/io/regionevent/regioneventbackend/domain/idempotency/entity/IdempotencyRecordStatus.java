package io.regionevent.regioneventbackend.domain.idempotency.entity;

public enum IdempotencyRecordStatus {

    PROCESSING,
    SUCCEEDED,
    FAILED
}
