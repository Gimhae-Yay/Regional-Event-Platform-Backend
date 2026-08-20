package io.regionevent.regioneventbackend.global.security.refresh;

import java.time.Instant;
import java.util.Objects;

public record RefreshToken(
    Long userId,
    Instant issuedAt,
    Instant expiresAt
) {

    public RefreshToken {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!issuedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

}
