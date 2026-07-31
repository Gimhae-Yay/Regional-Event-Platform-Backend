package io.regionevent.regioneventbackend.domain.idempotency.service;

import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyOperation;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

public record IdempotencyCommand(
    AppUser actor,
    IdempotencyOperation operation,
    String idempotencyKeyHash,
    String requestHash
) {

    public IdempotencyCommand {
        if (actor == null || actor.getUserId() == null) {
            throw new IllegalArgumentException("actor must be persisted");
        }
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        if (idempotencyKeyHash == null || idempotencyKeyHash.isBlank()) {
            throw new IllegalArgumentException("idempotencyKeyHash must not be blank");
        }
        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("requestHash must not be blank");
        }
    }
}
