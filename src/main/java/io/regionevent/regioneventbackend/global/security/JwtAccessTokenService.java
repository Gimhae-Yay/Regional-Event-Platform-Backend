package io.regionevent.regioneventbackend.global.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;

public class JwtAccessTokenService {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String JWT_TYPE = "JWT";

    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final String activeKeyId;
    private final SecretKey activeKey;
    private final Map<String, SecretKey> verificationKeys;
    private final Map<String, Instant> previousKeyVerificationEndTimes;

    public JwtAccessTokenService(JwtAccessTokenProperties properties, Clock clock) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        issuer = requireNotBlank(properties.getIssuer(), "issuer");
        audience = requireNotBlank(properties.getAudience(), "audience");
        activeKeyId = requireNotBlank(properties.getActiveKeyId(), "activeKeyId");
        activeKey = toSecretKey(properties.getActiveKey(), activeKeyId);
        VerificationKeys configuredVerificationKeys = createVerificationKeys(properties);
        verificationKeys = configuredVerificationKeys.keys();
        previousKeyVerificationEndTimes = configuredVerificationKeys.previousKeyVerificationEndTimes();
    }

    public String issue(Long userId) {
        validateUserId(userId);

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_TTL);
        return Jwts.builder()
            .header()
                .type(JWT_TYPE)
                .keyId(activeKeyId)
                .and()
            .issuer(issuer)
            .audience()
                .add(audience)
                .and()
            .subject(userId.toString())
            .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(activeKey, Jwts.SIG.HS256)
            .compact();
    }

    public AuthenticatedUser authenticate(String token) {
        try {
            Claims claims = Jwts.parser()
                .keyLocator(this::locateVerificationKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return validateClaims(claims);
        } catch (InvalidAccessTokenException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidAccessTokenException();
        }
    }

    private VerificationKeys createVerificationKeys(JwtAccessTokenProperties properties) {
        if (properties.getPreviousKeys().size() > 1) {
            throw new IllegalStateException("Only one previous JWT verification key is allowed");
        }

        Map<String, SecretKey> keys = new HashMap<>();
        Map<String, Instant> previousKeyVerificationEndTimes = new HashMap<>();
        keys.put(activeKeyId, activeKey);

        for (JwtAccessTokenProperties.VerificationKey previousKey : properties.getPreviousKeys()) {
            String keyId = requireNotBlank(previousKey.getKeyId(), "previousKeyId");
            SecretKey key = toSecretKey(previousKey.getKey(), keyId);
            Instant verificationEndsAt = requireVerificationEndsAt(previousKey.getVerificationEndsAt());
            if (verificationEndsAt.isAfter(clock.instant().plus(ACCESS_TOKEN_TTL))) {
                throw new IllegalStateException("Previous JWT verification key must expire within the access token lifetime");
            }
            if (keys.putIfAbsent(keyId, key) != null) {
                throw new IllegalStateException("Duplicate JWT verification key identifier");
            }
            previousKeyVerificationEndTimes.put(keyId, verificationEndsAt);
        }
        return new VerificationKeys(Map.copyOf(keys), Map.copyOf(previousKeyVerificationEndTimes));
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
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private SecretKey locateVerificationKey(Header header) {
        if (!Jwts.SIG.HS256.getId().equals(header.getAlgorithm())
            || !JWT_TYPE.equals(header.getType())) {
            throw new InvalidAccessTokenException();
        }

        Object keyIdValue = header.get("kid");
        if (!(keyIdValue instanceof String keyId)) {
            throw new InvalidAccessTokenException();
        }
        SecretKey verificationKey = verificationKeys.get(keyId);
        Instant verificationEndsAt = previousKeyVerificationEndTimes.get(keyId);
        if (verificationKey == null
            || verificationEndsAt != null && !clock.instant().isBefore(verificationEndsAt)) {
            throw new InvalidAccessTokenException();
        }
        return verificationKey;
    }

    private AuthenticatedUser validateClaims(Claims claims) {
        Instant now = clock.instant();
        Date issuedAt = requireClaim(claims.getIssuedAt());
        Date expiresAt = requireClaim(claims.getExpiration());
        Set<String> audiences = claims.getAudience();
        Instant issuedAtInstant = issuedAt.toInstant();
        Instant expiresAtInstant = expiresAt.toInstant();

        if (!issuer.equals(claims.getIssuer())
            || audiences == null
            || audiences.size() != 1
            || !audiences.contains(audience)
            || !ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))
            || issuedAtInstant.isAfter(now)
            || !expiresAtInstant.isAfter(now)
            || !expiresAtInstant.equals(issuedAtInstant.plus(ACCESS_TOKEN_TTL))) {
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

    private Instant requireVerificationEndsAt(Instant verificationEndsAt) {
        if (verificationEndsAt == null) {
            throw new IllegalStateException("previousKeyVerificationEndsAt must not be null");
        }
        return verificationEndsAt;
    }

    private record VerificationKeys(
        Map<String, SecretKey> keys,
        Map<String, Instant> previousKeyVerificationEndTimes
    ) {
    }

    public record AuthenticatedUser(Long userId) {

        public AuthenticatedUser {
            if (userId == null || userId <= 0) {
                throw new IllegalArgumentException("userId must be positive");
            }
        }
    }
}
