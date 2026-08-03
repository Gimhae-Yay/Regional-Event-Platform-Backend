package io.regionevent.regioneventbackend.global.security.refresh;

import static org.junit.jupiter.api.Assertions.assertAll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;

class JwtRefreshTokenServiceTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void 전체_단위_계약을_보존한다() {
        assertAll(
            () -> new JwtRefreshTokenServiceTest().issueAndAuthenticate_withValidRefreshToken_returnsRefreshToken(),
            () -> new JwtRefreshTokenServiceTest().authenticate_whenTokenUsesAccessProfile_throwsInvalidRefreshTokenException(),
            () -> new JwtRefreshTokenServiceTest().authenticate_whenTokenLifetimeExceeds14Days_throwsInvalidRefreshTokenException(),
            () -> new JwtRefreshTokenServiceTest().authenticate_whenTokenUsesPreviousRefreshKey_throwsInvalidRefreshTokenException()
        );
    }

    void issueAndAuthenticate_withValidRefreshToken_returnsRefreshToken() {
        JwtRefreshTokenService jwtRefreshTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        RefreshToken refreshToken = refreshToken();

        String token = jwtRefreshTokenService.issue(refreshToken);
        Claims claims = Jwts.parser()
            .verifyWith(toSecretKey(key((byte) 1)))
            .clock(() -> Date.from(ISSUED_AT))
            .build()
            .parseSignedClaims(token)
            .getPayload();

        assertThat(jwtRefreshTokenService.authenticate(token)).isEqualTo(refreshToken);
        assertThat(claims.getIssuer()).isEqualTo("regional-event-platform");
        assertThat(claims.getAudience()).containsExactly("regional-event-refresh");
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.getId()).isEqualTo(refreshToken.tokenId().toString());
        assertThat(claims.get("family_id", String.class)).isEqualTo(refreshToken.familyId().toString());
        assertThat(claims.get("token_type", String.class)).isEqualTo("REFRESH");
    }

    void authenticate_whenTokenUsesAccessProfile_throwsInvalidRefreshTokenException() {
        JwtRefreshTokenService jwtRefreshTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        String accessToken = createToken(
            "regional-event-api",
            "ACCESS",
            ISSUED_AT.plus(JwtRefreshTokenService.REFRESH_TOKEN_TTL),
            key((byte) 1)
        );

        assertThatThrownBy(() -> jwtRefreshTokenService.authenticate(accessToken))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    void authenticate_whenTokenLifetimeExceeds14Days_throwsInvalidRefreshTokenException() {
        JwtRefreshTokenService jwtRefreshTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        String longLivedToken = createToken(
            "regional-event-refresh",
            "REFRESH",
            ISSUED_AT.plus(JwtRefreshTokenService.REFRESH_TOKEN_TTL).plusSeconds(1),
            key((byte) 1)
        );

        assertThatThrownBy(() -> jwtRefreshTokenService.authenticate(longLivedToken))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    void authenticate_whenTokenUsesPreviousRefreshKey_throwsInvalidRefreshTokenException() {
        JwtRefreshTokenService jwtRefreshTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        String previousKeyToken = createToken(
            "regional-event-refresh",
            "REFRESH",
            ISSUED_AT.plus(Duration.ofDays(1)),
            key((byte) 2)
        );

        assertThatThrownBy(() -> jwtRefreshTokenService.authenticate(previousKeyToken))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    private JwtRefreshTokenService createService(Clock clock) {
        JwtRefreshTokenProperties properties = new JwtRefreshTokenProperties();
        properties.setIssuer("regional-event-platform");
        properties.setAudience("regional-event-refresh");
        properties.setActiveKeyId("refresh-test-key");
        properties.setActiveKey(key((byte) 1));
        return new JwtRefreshTokenService(properties, clock);
    }

    private RefreshToken refreshToken() {
        return new RefreshToken(
            1L,
            UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
            UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
            ISSUED_AT,
            ISSUED_AT.plus(JwtRefreshTokenService.REFRESH_TOKEN_TTL)
        );
    }

    private String createToken(String audience, String tokenType, Instant expiresAt, String encodedKey) {
        return Jwts.builder()
            .header()
                .type("JWT")
                .keyId("refresh-test-key")
                .and()
            .issuer("regional-event-platform")
            .audience()
                .add(audience)
                .and()
            .subject("1")
            .id(UUID.randomUUID().toString())
            .claim("family_id", UUID.randomUUID().toString())
            .claim("token_type", tokenType)
            .issuedAt(Date.from(ISSUED_AT))
            .expiration(Date.from(expiresAt))
            .signWith(toSecretKey(encodedKey), Jwts.SIG.HS256)
            .compact();
    }

    private SecretKey toSecretKey(String encodedKey) {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(encodedKey));
    }

    private String key(byte value) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, value);
        return Base64.getEncoder().encodeToString(key);
    }
}
