package io.regionevent.regioneventbackend.global.security.access;

import static org.junit.jupiter.api.Assertions.assertAll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;

class JwtAccessTokenServiceTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void 전체_단위_계약을_보존한다() {
        assertAll(
            () -> new JwtAccessTokenServiceTest().issueAndAuthenticate_withValidAccessToken_returnsAuthenticatedUser(),
            () -> new JwtAccessTokenServiceTest().authenticate_whenTokenIsExpired_throwsInvalidAccessTokenException(),
            () -> new JwtAccessTokenServiceTest().authenticate_whenAccessTokenLifetimeExceeds15Minutes_throwsInvalidAccessTokenException(),
            () -> new JwtAccessTokenServiceTest().authenticate_whenTokenTypeIsRefresh_throwsInvalidAccessTokenException(),
            () -> new JwtAccessTokenServiceTest().authenticate_whenIssuerOrAudienceDoesNotMatch_throwsInvalidAccessTokenException(),
            () -> new JwtAccessTokenServiceTest().authenticate_whenSignatureOrKeyIdOrAlgorithmIsInvalid_throwsInvalidAccessTokenException(),
            () -> new JwtAccessTokenServiceTest().authenticate_whenTokenUsesPreviousKey_returnsAuthenticatedUser(),
            () -> new JwtAccessTokenServiceTest().authenticate_whenPreviousKeyVerificationHasEnded_throwsInvalidAccessTokenException(),
            () -> new JwtAccessTokenServiceTest().authenticate_whenAuthoritiesClaimIsMissingInCompatibilityMode_returnsEmptyAuthorities(),
            () -> new JwtAccessTokenServiceTest().authenticate_whenAuthoritiesClaimIsMissingInStrictMode_throwsInvalidAccessTokenException(),
            () -> new JwtAccessTokenServiceTest().authenticate_whenAuthoritiesClaimIsMalformed_throwsInvalidAccessTokenException(),
            () -> new JwtAccessTokenServiceTest().createService_whenMoreThanOnePreviousKeyIsConfigured_throwsIllegalStateException(),
            () -> new JwtAccessTokenServiceTest().issue_whenUserIdIsNotPositive_throwsIllegalArgumentException(),
            () -> new JwtAccessTokenServiceTest().createService_whenPreviousKeyVerificationEndExceedsAccessTokenLifetime_throwsIllegalStateException()
        );
    }

    void issueAndAuthenticate_withValidAccessToken_returnsAuthenticatedUser() {
        JwtAccessTokenService jwtAccessTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));

        String token = jwtAccessTokenService.issue(1L, List.of(AccessTokenAuthority.VISITOR));
        Claims claims = Jwts.parser()
            .verifyWith(toSecretKey(key(0)))
            .clock(() -> Date.from(ISSUED_AT))
            .build()
            .parseSignedClaims(token)
            .getPayload();

        assertThat(jwtAccessTokenService.authenticate(token).userId()).isEqualTo(1L);
        assertThat(jwtAccessTokenService.authenticate(token).authorities())
            .containsExactly(AccessTokenAuthority.VISITOR);
        assertThat(claims.getIssuer()).isEqualTo("regional-event-platform");
        assertThat(claims.getAudience()).containsExactly("regional-event-api");
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("token_type", String.class)).isEqualTo("ACCESS");
        assertThat(claims.get("authorities", List.class)).containsExactly("ROLE_VISITOR");
        assertThat(claims.getExpiration()).isEqualTo(Date.from(ISSUED_AT.plusSeconds(900)));
        assertThat(claims).doesNotContainKeys("role", "region_id", "family_id", "jti");
    }

    void authenticate_whenTokenIsExpired_throwsInvalidAccessTokenException() {
        JwtAccessTokenService issuer = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        JwtAccessTokenService verifier = createService(Clock.fixed(ISSUED_AT.plusSeconds(900), ZoneOffset.UTC));

        assertThatThrownBy(() -> verifier.authenticate(issuer.issue(1L)))
            .isInstanceOf(InvalidAccessTokenException.class);
    }

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

    void authenticate_whenTokenUsesPreviousKey_returnsAuthenticatedUser() {
        JwtAccessTokenService oldKeyService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        JwtAccessTokenService rotatedKeyService = createRotatedKeyService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));

        assertThat(rotatedKeyService.authenticate(oldKeyService.issue(1L)).userId()).isEqualTo(1L);
    }

    void authenticate_whenPreviousKeyVerificationHasEnded_throwsInvalidAccessTokenException() {
        Clock verificationEndTime = Clock.fixed(ISSUED_AT.plus(Duration.ofMinutes(15)), ZoneOffset.UTC);
        JwtAccessTokenService oldKeyService = createService(verificationEndTime);
        JwtAccessTokenService rotatedKeyService = createRotatedKeyService(verificationEndTime);

        assertThatThrownBy(() -> rotatedKeyService.authenticate(oldKeyService.issue(1L)))
            .isInstanceOf(InvalidAccessTokenException.class);
    }

    void authenticate_whenAuthoritiesClaimIsMissingInCompatibilityMode_returnsEmptyAuthorities() {
        JwtAccessTokenService jwtAccessTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        String legacyToken = createToken(
            "regional-event-platform",
            "regional-event-api",
            "ACCESS",
            ISSUED_AT.plusSeconds(900),
            key(0)
        );

        assertThat(jwtAccessTokenService.authenticate(legacyToken).authorities()).isEmpty();
    }

    void authenticate_whenAuthoritiesClaimIsMissingInStrictMode_throwsInvalidAccessTokenException() {
        JwtAccessTokenService jwtAccessTokenService = createStrictService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        String legacyToken = createToken(
            "regional-event-platform",
            "regional-event-api",
            "ACCESS",
            ISSUED_AT.plusSeconds(900),
            key(0)
        );

        assertThatThrownBy(() -> jwtAccessTokenService.authenticate(legacyToken))
            .isInstanceOf(InvalidAccessTokenException.class);
    }

    void authenticate_whenAuthoritiesClaimIsMalformed_throwsInvalidAccessTokenException() {
        JwtAccessTokenService jwtAccessTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));

        assertThatThrownBy(() -> jwtAccessTokenService.authenticate(createTokenWithAuthorities("ROLE_VISITOR")))
            .isInstanceOf(InvalidAccessTokenException.class);
        assertThatThrownBy(() -> jwtAccessTokenService.authenticate(createTokenWithAuthorities(List.of(1))))
            .isInstanceOf(InvalidAccessTokenException.class);
        assertThatThrownBy(() -> jwtAccessTokenService.authenticate(createTokenWithAuthorities(
            java.util.Arrays.asList("ROLE_VISITOR", null)
        )))
            .isInstanceOf(InvalidAccessTokenException.class);
        assertThatThrownBy(() -> jwtAccessTokenService.authenticate(createTokenWithAuthorities(
            List.of("ROLE_VISITOR", "ROLE_VISITOR")
        )))
            .isInstanceOf(InvalidAccessTokenException.class);
        assertThatThrownBy(() -> jwtAccessTokenService.authenticate(createTokenWithAuthorities(List.of("ROLE_UNKNOWN"))))
            .isInstanceOf(InvalidAccessTokenException.class);
    }

    void createService_whenMoreThanOnePreviousKeyIsConfigured_throwsIllegalStateException() {
        JwtAccessTokenProperties properties = new JwtAccessTokenProperties();
        properties.setIssuer("regional-event-platform");
        properties.setAudience("regional-event-api");
        properties.setActiveKeyId("current-key");
        properties.setActiveKey(key(2));
        properties.setPreviousKeys(java.util.List.of(
            verificationKey("previous-key-1", key(0), ISSUED_AT.plus(Duration.ofMinutes(15))),
            verificationKey("previous-key-2", key(1), ISSUED_AT.plus(Duration.ofMinutes(15)))
        ));

        assertThatThrownBy(() -> new JwtAccessTokenService(properties, Clock.systemUTC()))
            .isInstanceOf(IllegalStateException.class);
    }

    void issue_whenUserIdIsNotPositive_throwsIllegalArgumentException() {
        JwtAccessTokenService jwtAccessTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));

        assertThatThrownBy(() -> jwtAccessTokenService.issue(0L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    void createService_whenPreviousKeyVerificationEndExceedsAccessTokenLifetime_throwsIllegalStateException() {
        JwtAccessTokenProperties properties = new JwtAccessTokenProperties();
        properties.setIssuer("regional-event-platform");
        properties.setAudience("regional-event-api");
        properties.setActiveKeyId("current-key");
        properties.setActiveKey(key(1));
        properties.setPreviousKeys(java.util.List.of(
            verificationKey("previous-key", key(0), ISSUED_AT.plus(Duration.ofMinutes(15)).plusSeconds(1))
        ));

        assertThatThrownBy(() -> new JwtAccessTokenService(properties, Clock.fixed(ISSUED_AT, ZoneOffset.UTC)))
            .isInstanceOf(IllegalStateException.class);
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

        properties.setPreviousKeys(java.util.List.of(
            verificationKey("test-key", key(0), ISSUED_AT.plus(Duration.ofMinutes(15)))
        ));
        return new JwtAccessTokenService(properties, clock);
    }

    private JwtAccessTokenService createStrictService(Clock clock) {
        JwtAccessTokenProperties properties = new JwtAccessTokenProperties();
        properties.setIssuer("regional-event-platform");
        properties.setAudience("regional-event-api");
        properties.setActiveKeyId("test-key");
        properties.setActiveKey(key(0));
        properties.setAuthoritiesClaimRequired(true);
        return new JwtAccessTokenService(properties, clock);
    }

    private JwtAccessTokenProperties.VerificationKey verificationKey(
        String keyId,
        String key,
        Instant verificationEndsAt
    ) {
        JwtAccessTokenProperties.VerificationKey verificationKey = new JwtAccessTokenProperties.VerificationKey();
        verificationKey.setKeyId(keyId);
        verificationKey.setKey(key);
        verificationKey.setVerificationEndsAt(verificationEndsAt);
        return verificationKey;
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

    private String createTokenWithAuthorities(Object authorities) {
        return Jwts.builder()
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
            .claim("authorities", authorities)
            .issuedAt(Date.from(ISSUED_AT))
            .expiration(Date.from(ISSUED_AT.plusSeconds(900)))
            .signWith(toSecretKey(key(0)), Jwts.SIG.HS256)
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
