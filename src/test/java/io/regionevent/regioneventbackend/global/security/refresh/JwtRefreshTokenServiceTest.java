package io.regionevent.regioneventbackend.global.security.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;

class JwtRefreshTokenServiceTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void issueAndAuthenticate_유효한토큰_사용자와절대만료를반환한다() {
        JwtRefreshTokenService jwtRefreshTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        RefreshToken refreshToken = new RefreshToken(
            1L,
            ISSUED_AT,
            ISSUED_AT.plus(JwtRefreshTokenService.REFRESH_TOKEN_TTL)
        );

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
        assertThat(claims.get("token_type", String.class)).isEqualTo("REFRESH");
        assertThat(claims.getId()).isNull();
        assertThat(claims.get("family_id")).isNull();
    }

    @Test
    void authenticate_14일과다른수명의토큰_인증실패한다() {
        JwtRefreshTokenService jwtRefreshTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        String token = createToken(ISSUED_AT.plus(Duration.ofDays(13)));

        assertThatThrownBy(() -> jwtRefreshTokenService.authenticate(token))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void authenticate_AccessToken프로필_인증실패한다() {
        JwtRefreshTokenService jwtRefreshTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        String token = Jwts.builder()
            .header()
                .type("JWT")
                .keyId("refresh-test-key")
                .and()
            .issuer("regional-event-platform")
            .audience()
                .add("regional-event-api")
                .and()
            .subject("1")
            .claim("token_type", "ACCESS")
            .issuedAt(Date.from(ISSUED_AT))
            .expiration(Date.from(ISSUED_AT.plus(JwtRefreshTokenService.REFRESH_TOKEN_TTL)))
            .signWith(toSecretKey(key((byte) 1)), Jwts.SIG.HS256)
            .compact();

        assertThatThrownBy(() -> jwtRefreshTokenService.authenticate(token))
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

    private String createToken(Instant expiresAt) {
        return Jwts.builder()
            .header()
                .type("JWT")
                .keyId("refresh-test-key")
                .and()
            .issuer("regional-event-platform")
            .audience()
                .add("regional-event-refresh")
                .and()
            .subject("1")
            .claim("token_type", "REFRESH")
            .issuedAt(Date.from(ISSUED_AT))
            .expiration(Date.from(expiresAt))
            .signWith(toSecretKey(key((byte) 1)), Jwts.SIG.HS256)
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
