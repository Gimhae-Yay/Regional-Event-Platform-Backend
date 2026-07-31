package io.regionevent.regioneventbackend.global.security.refresh;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RefreshToken(
    Long userId,
    UUID tokenId,
    UUID familyId,
    Instant issuedAt,
    Instant expiresAt
) {

    public RefreshToken {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Objects.requireNonNull(tokenId, "tokenId must not be null");
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!issuedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

    public RefreshToken rotate(UUID nextTokenId, Instant nextIssuedAt) {
        return new RefreshToken(userId, nextTokenId, familyId, nextIssuedAt, expiresAt);
    }
}
