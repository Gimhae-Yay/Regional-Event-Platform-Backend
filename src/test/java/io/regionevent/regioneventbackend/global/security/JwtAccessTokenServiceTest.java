package io.regionevent.regioneventbackend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.Test;

class JwtAccessTokenServiceTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void issueAndAuthenticate_withValidAccessToken_returnsAuthenticatedUser() throws Exception {
        JwtAccessTokenService jwtAccessTokenService = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));

        String token = jwtAccessTokenService.issue(1L);
        SignedJWT signedJwt = SignedJWT.parse(token);

        assertThat(jwtAccessTokenService.authenticate(token).userId()).isEqualTo(1L);
        assertThat(signedJwt.getJWTClaimsSet().getIssuer()).isEqualTo("regional-event-platform");
        assertThat(signedJwt.getJWTClaimsSet().getAudience()).containsExactly("regional-event-api");
        assertThat(signedJwt.getJWTClaimsSet().getSubject()).isEqualTo("1");
        assertThat(signedJwt.getJWTClaimsSet().getStringClaim("token_type")).isEqualTo("ACCESS");
        assertThat(signedJwt.getJWTClaimsSet().getExpirationTime()).isEqualTo(Date.from(ISSUED_AT.plusSeconds(900)));
        assertThat(signedJwt.getJWTClaimsSet().getClaims()).doesNotContainKeys("role", "region_id", "family_id", "jti");
    }

    @Test
    void authenticate_whenTokenIsExpired_throwsInvalidAccessTokenException() {
        JwtAccessTokenService issuer = createService(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        JwtAccessTokenService verifier = createService(Clock.fixed(ISSUED_AT.plusSeconds(900), ZoneOffset.UTC));

        assertThatThrownBy(() -> verifier.authenticate(issuer.issue(1L)))
            .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void authenticate_whenAccessTokenLifetimeExceeds15Minutes_throwsInvalidAccessTokenException() throws Exception {
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
    void authenticate_whenTokenTypeIsRefresh_throwsInvalidAccessTokenException() throws Exception {
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
    void authenticate_whenIssuerOrAudienceDoesNotMatch_throwsInvalidAccessTokenException() throws Exception {
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
    ) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject("1")
            .claim("token_type", tokenType)
            .issueTime(Date.from(ISSUED_AT))
            .expirationTime(Date.from(expiresAt))
            .build();
        SignedJWT signedJwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT)
                .keyID("test-key")
                .build(),
            claims
        );
        signedJwt.sign(new MACSigner(Base64.getDecoder().decode(encodedKey)));
        return signedJwt.serialize();
    }

    private String key(int value) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) value);
        return Base64.getEncoder().encodeToString(key);
    }
}
