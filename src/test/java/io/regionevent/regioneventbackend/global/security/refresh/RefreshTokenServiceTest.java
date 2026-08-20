package io.regionevent.regioneventbackend.global.security.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void issue_유효한사용자_14일절대만료토큰을발급한다() {
        JwtRefreshTokenService jwtRefreshTokenService = createJwtService();
        RefreshTokenService refreshTokenService = new RefreshTokenService(
            jwtRefreshTokenService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        String issuedToken = refreshTokenService.issue(1L);
        RefreshToken refreshToken = refreshTokenService.authenticate(issuedToken);

        assertThat(refreshToken.userId()).isEqualTo(1L);
        assertThat(refreshToken.issuedAt()).isEqualTo(NOW);
        assertThat(refreshToken.expiresAt()).isEqualTo(NOW.plus(JwtRefreshTokenService.REFRESH_TOKEN_TTL));
    }

    private JwtRefreshTokenService createJwtService() {
        JwtRefreshTokenProperties properties = new JwtRefreshTokenProperties();
        properties.setIssuer("regional-event-platform");
        properties.setAudience("regional-event-refresh");
        properties.setActiveKeyId("refresh-test-key");
        properties.setActiveKey("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        return new JwtRefreshTokenService(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
