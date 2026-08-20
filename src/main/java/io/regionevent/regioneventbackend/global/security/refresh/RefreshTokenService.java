package io.regionevent.regioneventbackend.global.security.refresh;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class RefreshTokenService {

    private final JwtRefreshTokenService jwtRefreshTokenService;
    private final Clock clock;

    public RefreshTokenService(
        JwtRefreshTokenService jwtRefreshTokenService,
        Clock clock
    ) {
        this.jwtRefreshTokenService = Objects.requireNonNull(jwtRefreshTokenService, "jwtRefreshTokenService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public String issue(Long userId) {
        validateUserId(userId);

        Instant issuedAt = currentInstant();
        RefreshToken refreshToken = new RefreshToken(
            userId,
            issuedAt,
            issuedAt.plus(JwtRefreshTokenService.REFRESH_TOKEN_TTL)
        );
        return jwtRefreshTokenService.issue(refreshToken);
    }

    public RefreshToken authenticate(String token) {
        return jwtRefreshTokenService.authenticate(token);
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
