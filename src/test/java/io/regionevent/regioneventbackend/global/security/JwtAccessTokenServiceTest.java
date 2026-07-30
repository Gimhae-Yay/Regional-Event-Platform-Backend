package io.regionevent.regioneventbackend.global.security;

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

class JwtAccessTokenServiceTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void issueAndAuthenticate_withValidAccessToken_returnsAuthenticatedUser() {
        JwtAccessTokenService jwtAccessTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));

        String token = jwtAccessTokenService.issue(1L);
        Claims claims = Jwts.parser()
            .verifyWith(toSecretKey(key(0)))
            .clock(() -> Date.from(ISSUED_AT))
            .build()
            .parseSignedClaims(token)
            .getPayload();

        assertThat(jwtAccessTokenService.authenticate(token).userId()).isEqualTo(1L);
        assertThat(claims.getIssuer()).isEqualTo("regional-event-platform");
        assertThat(claims.getAudience()).containsExactly("regional-event-api");
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("token_type", String.class)).isEqualTo("ACCESS");
        assertThat(claims.getExpiration()).isEqualTo(Date.from(ISSUED_AT.plusSeconds(900)));
        assertThat(claims).doesNotContainKeys("role", "region_id", "family_id", "jti");
    }

    @Test
    void authenticate_whenTokenIsExpired_throwsInvalidAccessTokenException() {
        JwtAccessTokenService issuer = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        JwtAccessTokenService verifier = createService(Clock.fixed(ISSUED_AT.plusSeconds(900), ZoneOffset.UTC));

        assertThatThrownBy(() -> verifier.authenticate(issuer.issue(1L)))
            .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void authenticate_whenAccessTokenLifetimeExceeds15Minutes_throwsInvalidAccessTokenException() {
        JwtAccessTokenService jwtAccessTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        String longLivedToken = createToken(
            "regional-event-platform",
            "regional-event-api",
            "ACCESS",
            ISSUED_AT.plus(Duration.ofDays(1)),
            key(0)
        );

        assertThatThrownBy(() -> jwtAccessTokenService.authenticate(longLivedToken))
            .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void authenticate_whenTokenTypeIsRefresh_throwsInvalidAccessTokenException() {
        JwtAccessTokenService jwtAccessTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        String refreshToken = createToken(
            "regional-event-platform",
            "regional-event-api",
            "REFRESH",
            ISSUED_AT.plusSeconds(900),
            key(0)
        );

        assertThatThrownBy(() -> jwtAccessTokenService.authenticate(refreshToken))
            .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void authenticate_whenIssuerOrAudienceDoesNotMatch_throwsInvalidAccessTokenException() {
        JwtAccessTokenService jwtAccessTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        String wrongIssuerToken = createToken(
            "other-platform",
            "regional-event-api",
            "ACCESS",
            ISSUED_AT.plusSeconds(900),
            key(0)
        );
        String wrongAudienceToken = createToken(
            "regional-event-platform",
            "other-api",
            "ACCESS",
            ISSUED_AT.plusSeconds(900),
            key(0)
        );

        assertThatThrownBy(() -> jwtAccessTokenService.authenticate(wrongIssuerToken))
            .isInstanceOf(InvalidAccessTokenException.class);
        assertThatThrownBy(() -> jwtAccessTokenService.authenticate(wrongAudienceToken))
            .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void authenticate_whenSignatureOrKeyIdOrAlgorithmIsInvalid_throwsInvalidAccessTokenException() {
        JwtAccessTokenService jwtAccessTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        String forgedToken = createToken(
            "regional-event-platform",
            "regional-event-api",
            "ACCESS",
            ISSUED_AT.plusSeconds(900),
            key(1)
        );
        String unknownKeyToken = createToken(
            "regional-event-platform",
            "regional-event-api",
            "ACCESS",
            ISSUED_AT.plusSeconds(900),
            "unknown-key",
            key(0)
        );
        String hs384Token = Jwts.builder()
            .header()
                .type("JWT")
                .keyId("test-key")
                .and()
            .issuer("regional-event-platform")
            .audience()
                .add("regional-event-api")
                .and()
            .subject("1")
            .claim("token_type", "ACCESS")
            .issuedAt(Date.from(ISSUED_AT))
            .expiration(Date.from(ISSUED_AT.plusSeconds(900)))
            .signWith(toSecretKey(key(2, 48)), Jwts.SIG.HS384)
            .compact();

        assertThatThrownBy(() -> jwtAccessTokenService.authenticate(forgedToken))
            .isInstanceOf(InvalidAccessTokenException.class);
        assertThatThrownBy(() -> jwtAccessTokenService.authenticate(unknownKeyToken))
            .isInstanceOf(InvalidAccessTokenException.class);
        assertThatThrownBy(() -> jwtAccessTokenService.authenticate(hs384Token))
            .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void authenticate_whenTokenUsesPreviousKey_returnsAuthenticatedUser() {
        JwtAccessTokenService oldKeyService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        JwtAccessTokenService rotatedKeyService = createRotatedKeyService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));

        assertThat(rotatedKeyService.authenticate(oldKeyService.issue(1L)).userId()).isEqualTo(1L);
    }

    @Test
    void issue_whenUserIdIsNotPositive_throwsIllegalArgumentException() {
        JwtAccessTokenService jwtAccessTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));

        assertThatThrownBy(() -> jwtAccessTokenService.issue(0L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private JwtAccessTokenService createService(Clock clock) {
        JwtAccessTokenProperties properties = new JwtAccessTokenProperties();
        properties.setIssuer("regional-event-platform");
        properties.setAudience("regional-event-api");
        properties.setActiveKeyId("test-key");
        properties.setActiveKey(key(0));
        return new JwtAccessTokenService(properties, clock);
    }

    private JwtAccessTokenService createRotatedKeyService(Clock clock) {
        JwtAccessTokenProperties properties = new JwtAccessTokenProperties();
        properties.setIssuer("regional-event-platform");
        properties.setAudience("regional-event-api");
        properties.setActiveKeyId("current-key");
        properties.setActiveKey(key(1));

        JwtAccessTokenProperties.VerificationKey previousKey = new JwtAccessTokenProperties.VerificationKey();
        previousKey.setKeyId("test-key");
        previousKey.setKey(key(0));
        properties.setPreviousKeys(java.util.List.of(previousKey));
        return new JwtAccessTokenService(properties, clock);
    }

    private String createToken(
        String issuer,
        String audience,
        String tokenType,
        Instant expiresAt,
        String encodedKey
    ) {
        return createToken(issuer, audience, tokenType, expiresAt, "test-key", encodedKey);
    }

    private String createToken(
        String issuer,
        String audience,
        String tokenType,
        Instant expiresAt,
        String keyId,
        String encodedKey
    ) {
        return Jwts.builder()
            .header()
                .type("JWT")
                .keyId(keyId)
                .and()
            .issuer(issuer)
            .audience()
                .add(audience)
                .and()
            .subject("1")
            .claim("token_type", tokenType)
            .issuedAt(Date.from(ISSUED_AT))
            .expiration(Date.from(expiresAt))
            .signWith(toSecretKey(encodedKey), Jwts.SIG.HS256)
            .compact();
    }

    private SecretKey toSecretKey(String encodedKey) {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(encodedKey));
    }

    private String key(int value) {
        return key(value, 32);
    }

    private String key(int value, int length) {
        byte[] key = new byte[length];
        java.util.Arrays.fill(key, (byte) value);
        return Base64.getEncoder().encodeToString(key);
    }
}
