package io.regionevent.regioneventbackend.global.security.refresh;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class RefreshTokenService {

    private final JwtRefreshTokenService jwtRefreshTokenService;
    private final RefreshTokenStore refreshTokenStore;
    private final Clock clock;

    public RefreshTokenService(
        JwtRefreshTokenService jwtRefreshTokenService,
        RefreshTokenStore refreshTokenStore,
        Clock clock
    ) {
        this.jwtRefreshTokenService = Objects.requireNonNull(jwtRefreshTokenService, "jwtRefreshTokenService must not be null");
        this.refreshTokenStore = Objects.requireNonNull(refreshTokenStore, "refreshTokenStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public String issue(Long userId) {
        validateUserId(userId);

        Instant issuedAt = currentInstant();
        RefreshToken refreshToken = new RefreshToken(
            userId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            issuedAt,
            issuedAt.plus(JwtRefreshTokenService.REFRESH_TOKEN_TTL)
        );
        String token = jwtRefreshTokenService.issue(refreshToken);
        refreshTokenStore.createFamily(refreshToken);
        return token;
    }

    public String rotate(String token) {
        return rotate(token, ignored -> {
        });
    }

    public String rotate(String token, Consumer<RefreshToken> beforeCompletion) {
        Objects.requireNonNull(beforeCompletion, "beforeCompletion must not be null");

        RefreshToken currentToken = jwtRefreshTokenService.authenticate(token);
        UUID attemptId = UUID.randomUUID();
        RefreshTokenStore.RotationStartResult result = refreshTokenStore.startRotation(currentToken, attemptId);
        if (result == RefreshTokenStore.RotationStartResult.CONFLICT) {
            throw new RefreshTokenConflictException();
        }
        if (result == RefreshTokenStore.RotationStartResult.INVALID) {
            throw new InvalidRefreshTokenException();
        }

        try {
            beforeCompletion.accept(currentToken);

            RefreshToken nextToken = currentToken.rotate(UUID.randomUUID(), currentInstant());
            String rotatedToken = jwtRefreshTokenService.issue(nextToken);
            RefreshTokenStore.RotationCompletionResult completionResult = refreshTokenStore.completeRotation(
                currentToken,
                nextToken.tokenId(),
                attemptId
            );
            if (completionResult == RefreshTokenStore.RotationCompletionResult.CONFLICT) {
                throw new RefreshTokenConflictException();
            }
            if (completionResult == RefreshTokenStore.RotationCompletionResult.INVALID) {
                throw new InvalidRefreshTokenException();
            }
            return rotatedToken;
        } catch (RuntimeException exception) {
            refreshTokenStore.cancelRotation(currentToken, attemptId);
            throw exception;
        }
    }

    public void revokeCurrentFamily(String token) {
        refreshTokenStore.revokeFamily(jwtRefreshTokenService.authenticate(token));
    }

    public void revokeAllFamilies(Long userId) {
        validateUserId(userId);
        refreshTokenStore.revokeAllFamilies(userId);
    }

    private Instant currentInstant() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }
}
