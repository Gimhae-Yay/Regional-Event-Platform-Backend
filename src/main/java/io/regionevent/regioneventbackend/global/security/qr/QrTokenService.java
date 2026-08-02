package io.regionevent.regioneventbackend.global.security.qr;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class QrTokenService {

    private static final String TOKEN_VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MAX_TOKEN_TTL_SECONDS = 300;
    private static final int MAX_TOKEN_LENGTH = 1_024;
    private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern PAYLOAD_PATTERN = Pattern.compile(
        "\\{\\\"qrReference\\\":\\\"([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\\\","
            + "\\\"sessionId\\\":([1-9]\\d*),\\\"expiresAtEpochMillis\\\":([1-9]\\d*)}"
    );

    private final Duration tokenTtl;
    private final String activeKeyId;
    private final SecretKey activeKey;
    private final Map<String, VerificationKey> verificationKeys;

    public QrTokenService(QrTokenProperties properties, Clock clock) {
        Objects.requireNonNull(properties, "properties must not be null");
        Clock validatedClock = Objects.requireNonNull(clock, "clock must not be null");
        tokenTtl = validateTokenTtl(properties.getTokenTtl());
        activeKeyId = requireValidKeyId(properties.getActiveKeyId());
        activeKey = toSecretKey(properties.getActiveKey());
        verificationKeys = createVerificationKeys(properties, validatedClock);
    }

    public IssuedQrToken issue(
        String qrReference,
        Long sessionId,
        Instant issuedAt,
        Instant checkinClosesAt
    ) {
        String validatedQrReference = requireQrReference(qrReference);
        Long validatedSessionId = requirePositive(sessionId, "sessionId");
        Instant validatedIssuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Instant validatedCheckinClosesAt = Objects.requireNonNull(
            checkinClosesAt,
            "checkinClosesAt must not be null"
        );
        Instant expiresAt = validatedIssuedAt.plus(tokenTtl);
        if (validatedCheckinClosesAt.isBefore(expiresAt)) {
            expiresAt = validatedCheckinClosesAt;
        }
        if (!validatedIssuedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("checkinClosesAt must be after issuedAt");
        }

        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            payload(validatedQrReference, validatedSessionId, expiresAt.toEpochMilli()).getBytes(StandardCharsets.UTF_8)
        );
        String signingInput = TOKEN_VERSION + "." + activeKeyId + "." + payload;
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(sign(activeKey, signingInput));
        return new IssuedQrToken(signingInput + "." + signature, expiresAt);
    }

    public VerificationResult verify(String token, Instant verifiedAt) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return new Rejected(VerificationFailure.MALFORMED);
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 4 || !TOKEN_VERSION.equals(parts[0]) || !KEY_ID_PATTERN.matcher(parts[1]).matches()) {
            return new Rejected(parts.length == 4 && !TOKEN_VERSION.equals(parts[0])
                ? VerificationFailure.VERSION_UNSUPPORTED
                : VerificationFailure.MALFORMED);
        }

        Instant validatedVerifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
        VerificationKey verificationKey = verificationKeys.get(parts[1]);
        if (verificationKey == null
            || verificationKey.verificationEndsAt() != null
                && !validatedVerifiedAt.isBefore(verificationKey.verificationEndsAt())) {
            return new Rejected(VerificationFailure.KEY_UNKNOWN);
        }

        byte[] providedSignature;
        try {
            providedSignature = Base64.getUrlDecoder().decode(parts[3]);
        } catch (IllegalArgumentException exception) {
            return new Rejected(VerificationFailure.MALFORMED);
        }
        if (!MessageDigest.isEqual(providedSignature, sign(verificationKey.key(), String.join(".", parts[0], parts[1], parts[2])))) {
            return new Rejected(VerificationFailure.SIGNATURE_INVALID);
        }

        QrTokenClaims claims = parsePayload(parts[2]);
        if (claims == null) {
            return new Rejected(VerificationFailure.MALFORMED);
        }
        if (!validatedVerifiedAt.isBefore(claims.expiresAt())) {
            return new Rejected(VerificationFailure.EXPIRED);
        }
        return new Verified(claims);
    }

    private Map<String, VerificationKey> createVerificationKeys(
        QrTokenProperties properties,
        Clock clock
    ) {
        if (properties.getPreviousKeys().size() > 1) {
            throw new IllegalStateException("Only one previous QR verification key is allowed");
        }

        Map<String, VerificationKey> keys = new HashMap<>();
        keys.put(activeKeyId, new VerificationKey(activeKey, null));
        for (QrTokenProperties.VerificationKey previousKey : properties.getPreviousKeys()) {
            String keyId = requireValidKeyId(previousKey.getKeyId());
            Instant verificationEndsAt = Objects.requireNonNull(
                previousKey.getVerificationEndsAt(),
                "previousKeyVerificationEndsAt must not be null"
            );
            if (verificationEndsAt.isAfter(clock.instant().plus(tokenTtl))) {
                throw new IllegalStateException("Previous QR verification key must expire within the token lifetime");
            }
            if (keys.putIfAbsent(keyId, new VerificationKey(toSecretKey(previousKey.getKey()), verificationEndsAt)) != null) {
                throw new IllegalStateException("Duplicate QR verification key identifier");
            }
        }
        return Map.copyOf(keys);
    }

    private static String payload(String qrReference, Long sessionId, long expiresAtEpochMillis) {
        return "{\"qrReference\":\"%s\",\"sessionId\":%d,\"expiresAtEpochMillis\":%d}"
            .formatted(qrReference, sessionId, expiresAtEpochMillis);
    }

    private static QrTokenClaims parsePayload(String encodedPayload) {
        try {
            String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            Matcher matcher = PAYLOAD_PATTERN.matcher(payload);
            if (!matcher.matches()) {
                return null;
            }
            return new QrTokenClaims(
                matcher.group(1),
                Long.valueOf(matcher.group(2)),
                Instant.ofEpochMilli(Long.parseLong(matcher.group(3)))
            );
        } catch (IllegalArgumentException | DateTimeException exception) {
            return null;
        }
    }

    private static byte[] sign(SecretKey key, String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign QR token", exception);
        }
    }

    private static SecretKey toSecretKey(String encodedKey) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(requireNotBlank(encodedKey, "key"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid QR key configuration", exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("QR key must contain at least 256 bits");
        }
        return new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }

    private static Duration validateTokenTtl(Duration tokenTtl) {
        if (tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()
            || tokenTtl.compareTo(Duration.ofSeconds(MAX_TOKEN_TTL_SECONDS)) > 0) {
            throw new IllegalStateException("QR token TTL must be greater than zero and at most five minutes");
        }
        return tokenTtl;
    }

    private static String requireQrReference(String qrReference) {
        try {
            UUID uuid = UUID.fromString(requireNotBlank(qrReference, "qrReference"));
            if (uuid.version() != 4 || !uuid.toString().equals(qrReference)) {
                throw new IllegalArgumentException("qrReference must be a canonical UUID v4");
            }
            return qrReference;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("qrReference must be a canonical UUID v4", exception);
        }
    }

    private static Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static String requireValidKeyId(String keyId) {
        if (keyId == null || !KEY_ID_PATTERN.matcher(keyId).matches()) {
            throw new IllegalStateException("QR key identifier must contain 1 to 64 URL-safe characters");
        }
        return keyId;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }
        return value;
    }

    public record IssuedQrToken(String token, Instant expiresAt) {

        public IssuedQrToken {
            Objects.requireNonNull(token, "token must not be null");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }
    }

    public sealed interface VerificationResult permits Verified, Rejected {
    }

    public record Verified(QrTokenClaims claims) implements VerificationResult {

        public Verified {
            Objects.requireNonNull(claims, "claims must not be null");
        }
    }

    public record Rejected(VerificationFailure failure) implements VerificationResult {

        public Rejected {
            Objects.requireNonNull(failure, "failure must not be null");
        }
    }

    public record QrTokenClaims(
        String qrReference,
        Long sessionId,
        Instant expiresAt
    ) {

        public QrTokenClaims {
            requireQrReference(qrReference);
            requirePositive(sessionId, "sessionId");
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }
    }

    public enum VerificationFailure {
        MALFORMED,
        VERSION_UNSUPPORTED,
        KEY_UNKNOWN,
        SIGNATURE_INVALID,
        EXPIRED
    }

    private record VerificationKey(
        SecretKey key,
        Instant verificationEndsAt
    ) {
    }
}
