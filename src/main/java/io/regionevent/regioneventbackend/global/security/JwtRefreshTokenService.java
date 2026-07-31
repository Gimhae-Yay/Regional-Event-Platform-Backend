package io.regionevent.regioneventbackend.global.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;

public class JwtRefreshTokenService {

    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String FAMILY_ID_CLAIM = "family_id";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";
    private static final String JWT_TYPE = "JWT";

    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final String activeKeyId;
    private final SecretKey activeKey;

    public JwtRefreshTokenService(JwtRefreshTokenProperties properties, Clock clock) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        issuer = requireNotBlank(properties.getIssuer(), "issuer");
        audience = requireNotBlank(properties.getAudience(), "audience");
        activeKeyId = requireNotBlank(properties.getActiveKeyId(), "activeKeyId");
        activeKey = toSecretKey(properties.getActiveKey());
    }

    public String issue(RefreshToken refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");

        return Jwts.builder()
            .header()
                .type(JWT_TYPE)
                .keyId(activeKeyId)
                .and()
            .issuer(issuer)
            .audience()
                .add(audience)
                .and()
            .subject(refreshToken.userId().toString())
            .id(refreshToken.tokenId().toString())
            .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
            .claim(FAMILY_ID_CLAIM, refreshToken.familyId().toString())
            .issuedAt(Date.from(refreshToken.issuedAt()))
            .expiration(Date.from(refreshToken.expiresAt()))
            .signWith(activeKey, Jwts.SIG.HS256)
            .compact();
    }

    public RefreshToken authenticate(String token) {
        try {
            Claims claims = Jwts.parser()
                .keyLocator(this::locateVerificationKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return validateClaims(claims);
        } catch (InvalidRefreshTokenException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidRefreshTokenException();
        }
    }

    private SecretKey locateVerificationKey(Header header) {
        if (!Jwts.SIG.HS256.getId().equals(header.getAlgorithm())
            || !JWT_TYPE.equals(header.getType())
            || !activeKeyId.equals(header.get("kid"))) {
            throw new InvalidRefreshTokenException();
        }
        return activeKey;
    }

    private RefreshToken validateClaims(Claims claims) {
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
            || !REFRESH_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))
            || issuedAtInstant.isAfter(now)
            || !expiresAtInstant.isAfter(now)
            || expiresAtInstant.isAfter(issuedAtInstant.plus(REFRESH_TOKEN_TTL))) {
            throw new InvalidRefreshTokenException();
        }
        return new RefreshToken(
            toUserId(claims.getSubject()),
            toUuid(claims.getId()),
            toUuid(claims.get(FAMILY_ID_CLAIM, String.class)),
            issuedAtInstant,
            expiresAtInstant
        );
    }

    private SecretKey toSecretKey(String encodedKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(requireNotBlank(encodedKey, "activeKey"));
            if (keyBytes.length < 32) {
                throw new IllegalStateException("JWT key must contain at least 256 bits");
            }
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid JWT key configuration", exception);
        }
    }

    private Date requireClaim(Date value) {
        if (value == null) {
            throw new InvalidRefreshTokenException();
        }
        return value;
    }

    private Long toUserId(String subject) {
        if (subject == null || !subject.matches("[1-9]\\d*")) {
            throw new InvalidRefreshTokenException();
        }
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new InvalidRefreshTokenException();
        }
    }

    private UUID toUuid(String value) {
        if (value == null) {
            throw new InvalidRefreshTokenException();
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRefreshTokenException();
        }
    }

    private String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }
        return value;
    }
}
