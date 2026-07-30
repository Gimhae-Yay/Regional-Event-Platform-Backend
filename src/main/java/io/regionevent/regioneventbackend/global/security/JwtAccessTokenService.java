package io.regionevent.regioneventbackend.global.security;

import java.security.Key;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public class JwtAccessTokenService {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final String activeKeyId;
    private final SecretKey activeKey;
    private final Map<String, SecretKey> verificationKeys;

    public JwtAccessTokenService(JwtAccessTokenProperties properties, Clock clock) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        issuer = requireNotBlank(properties.getIssuer(), "issuer");
        audience = requireNotBlank(properties.getAudience(), "audience");
        activeKeyId = requireNotBlank(properties.getActiveKeyId(), "activeKeyId");
        activeKey = toSecretKey(properties.getActiveKey(), activeKeyId);
        verificationKeys = createVerificationKeys(properties);
    }

    public String issue(Long userId) {
        validateUserId(userId);

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_TTL);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(userId.toString())
            .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
            .type(JOSEObjectType.JWT)
            .keyID(activeKeyId)
            .build();
        SignedJWT signedJwt = new SignedJWT(header, claims);

        try {
            signedJwt.sign(new MACSigner(activeKey));
            return signedJwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Failed to issue access token", exception);
        }
    }

    public AuthenticatedUser authenticate(String token) {
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            validateHeader(signedJwt.getHeader());
            validateSignature(signedJwt);
            return validateClaims(signedJwt.getJWTClaimsSet());
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw new InvalidAccessTokenException();
        }
    }

    private Map<String, SecretKey> createVerificationKeys(JwtAccessTokenProperties properties) {
        Map<String, SecretKey> keys = new HashMap<>();
        keys.put(activeKeyId, activeKey);

        for (JwtAccessTokenProperties.VerificationKey previousKey : properties.getPreviousKeys()) {
            String keyId = requireNotBlank(previousKey.getKeyId(), "previousKeyId");
            SecretKey key = toSecretKey(previousKey.getKey(), keyId);
            if (keys.putIfAbsent(keyId, key) != null) {
                throw new IllegalStateException("Duplicate JWT verification key identifier");
            }
        }
        return Map.copyOf(keys);
    }

    private SecretKey toSecretKey(String encodedKey, String keyId) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(requireNotBlank(encodedKey, "key"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid JWT key configuration", exception);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT key must contain at least 256 bits");
        }
        if (keyId.isBlank()) {
            throw new IllegalStateException("JWT key identifier must not be blank");
        }
        return new SecretKeySpec(keyBytes, HMAC_SHA_256);
    }

    private void validateHeader(JWSHeader header) {
        if (!JWSAlgorithm.HS256.equals(header.getAlgorithm())) {
            throw new InvalidAccessTokenException();
        }
        if (!JOSEObjectType.JWT.equals(header.getType())) {
            throw new InvalidAccessTokenException();
        }
        if (header.getKeyID() == null || !verificationKeys.containsKey(header.getKeyID())) {
            throw new InvalidAccessTokenException();
        }
    }

    private void validateSignature(SignedJWT signedJwt) throws JOSEException {
        Key verificationKey = verificationKeys.get(signedJwt.getHeader().getKeyID());
        if (!signedJwt.verify(new MACVerifier(verificationKey.getEncoded()))) {
            throw new InvalidAccessTokenException();
        }
    }

    private AuthenticatedUser validateClaims(JWTClaimsSet claims) throws ParseException {
        Instant now = clock.instant();
        Date issuedAt = requireClaim(claims.getIssueTime());
        Date expiresAt = requireClaim(claims.getExpirationTime());

        if (!issuer.equals(claims.getIssuer())
            || !List.of(audience).equals(claims.getAudience())
            || !ACCESS_TOKEN_TYPE.equals(claims.getStringClaim(TOKEN_TYPE_CLAIM))
            || issuedAt.toInstant().isAfter(now)
            || !expiresAt.toInstant().isAfter(now)) {
            throw new InvalidAccessTokenException();
        }
        return new AuthenticatedUser(toUserId(claims.getSubject()));
    }

    private Date requireClaim(Date value) {
        if (value == null) {
            throw new InvalidAccessTokenException();
        }
        return value;
    }

    private Long toUserId(String subject) {
        if (subject == null || !subject.matches("[1-9]\\d*")) {
            throw new InvalidAccessTokenException();
        }

        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new InvalidAccessTokenException();
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }

    private String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }
        return value;
    }

    public record AuthenticatedUser(Long userId) {

        public AuthenticatedUser {
            if (userId == null || userId <= 0) {
                throw new IllegalArgumentException("userId must be positive");
            }
        }
    }
}
