package io.regionevent.regioneventbackend.domain.idempotency.service;

import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecord;

public sealed interface IdempotencyAcquireResult {

    record Acquired(IdempotencyRecord record) implements IdempotencyAcquireResult {
    }

    record Succeeded(IdempotencyRecord record) implements IdempotencyAcquireResult {
    }

    record Failed(IdempotencyRecord record) implements IdempotencyAcquireResult {
    }

    record KeyConflict() implements IdempotencyAcquireResult {
    }

    record InProgress() implements IdempotencyAcquireResult {
    }
}
