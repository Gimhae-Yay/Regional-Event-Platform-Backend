package io.regionevent.regioneventbackend.global.security.qr;

import static org.junit.jupiter.api.Assertions.assertAll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

class QrTokenServiceTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-02T01:00:00.123Z");
    private static final String QR_REFERENCE = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    void 전체_단위_계약을_보존한다() {
        assertAll(
            () -> new QrTokenServiceTest().issueAndVerify_whenTokenIsValid_returnsSignedClaims(),
            () -> new QrTokenServiceTest().issue_whenCheckinClosesBeforeTokenTtl_usesCheckinCloseAt(),
            () -> new QrTokenServiceTest().verify_whenTokenVersionIsUnsupported_returnsVersionFailure(),
            () -> new QrTokenServiceTest().verify_whenTokenIsMalformed_returnsMalformedFailure(),
            () -> new QrTokenServiceTest().verify_whenKeyIsUnknown_returnsKeyFailure(),
            () -> new QrTokenServiceTest().verify_whenSignatureIsModified_returnsSignatureFailure(),
            () -> new QrTokenServiceTest().verify_whenTokenIsExpired_returnsExpiredFailure(),
            () -> new QrTokenServiceTest().verify_whenTokenUsesPreviousKeyBeforeVerificationEnd_returnsSignedClaims(),
            () -> new QrTokenServiceTest().verify_whenPreviousKeyVerificationHasEnded_returnsKeyFailure(),
            () -> new QrTokenServiceTest().createService_whenTtlExceedsFiveMinutes_throwsIllegalStateException()
        );
    }

    void issueAndVerify_whenTokenIsValid_returnsSignedClaims() {
        QrTokenService qrTokenService = createService("current-key", key(1), Duration.ofMinutes(5), List.of());

        QrTokenService.IssuedQrToken issuedToken = qrTokenService.issue(
            QR_REFERENCE,
            456L,
            ISSUED_AT,
            ISSUED_AT.plus(Duration.ofMinutes(10))
        );

        assertThat(issuedToken.token()).startsWith("v1.current-key.");
        assertThat(issuedToken.expiresAt()).isEqualTo(ISSUED_AT.plus(Duration.ofMinutes(5)));
        assertThat(qrTokenService.verify(issuedToken.token(), ISSUED_AT.plusSeconds(1)))
            .isEqualTo(new QrTokenService.Verified(new QrTokenService.QrTokenClaims(
                QR_REFERENCE,
                456L,
                issuedToken.expiresAt()
            )));
    }

    void issue_whenCheckinClosesBeforeTokenTtl_usesCheckinCloseAt() {
        QrTokenService qrTokenService = createService("current-key", key(1), Duration.ofMinutes(5), List.of());
        Instant checkinClosesAt = ISSUED_AT.plusMillis(1_234);

        QrTokenService.IssuedQrToken issuedToken = qrTokenService.issue(
            QR_REFERENCE,
            456L,
            ISSUED_AT,
            checkinClosesAt
        );

        assertThat(issuedToken.expiresAt()).isEqualTo(checkinClosesAt);
    }

    void verify_whenTokenVersionIsUnsupported_returnsVersionFailure() {
        QrTokenService qrTokenService = createService("current-key", key(1), Duration.ofMinutes(5), List.of());
        String token = issue(qrTokenService);

        assertThat(qrTokenService.verify(replaceSegment(token, 0, "v2"), ISSUED_AT.plusSeconds(1)))
            .isEqualTo(new QrTokenService.Rejected(QrTokenService.VerificationFailure.VERSION_UNSUPPORTED));
    }

    void verify_whenTokenIsMalformed_returnsMalformedFailure() {
        QrTokenService qrTokenService = createService("current-key", key(1), Duration.ofMinutes(5), List.of());

        assertThat(qrTokenService.verify("v1.current-key", ISSUED_AT.plusSeconds(1)))
            .isEqualTo(new QrTokenService.Rejected(QrTokenService.VerificationFailure.MALFORMED));
    }

    void verify_whenKeyIsUnknown_returnsKeyFailure() {
        QrTokenService qrTokenService = createService("current-key", key(1), Duration.ofMinutes(5), List.of());
        String token = issue(qrTokenService);

        assertThat(qrTokenService.verify(replaceSegment(token, 1, "unknown-key"), ISSUED_AT.plusSeconds(1)))
            .isEqualTo(new QrTokenService.Rejected(QrTokenService.VerificationFailure.KEY_UNKNOWN));
    }

    void verify_whenSignatureIsModified_returnsSignatureFailure() {
        QrTokenService qrTokenService = createService("current-key", key(1), Duration.ofMinutes(5), List.of());
        String token = issue(qrTokenService);
        String signature = token.substring(token.lastIndexOf('.') + 1);
        String modifiedSignature = (signature.startsWith("A") ? "B" : "A") + signature.substring(1);

        assertThat(qrTokenService.verify(replaceSegment(token, 3, modifiedSignature), ISSUED_AT.plusSeconds(1)))
            .isEqualTo(new QrTokenService.Rejected(QrTokenService.VerificationFailure.SIGNATURE_INVALID));
    }

    void verify_whenTokenIsExpired_returnsExpiredFailure() {
        QrTokenService qrTokenService = createService("current-key", key(1), Duration.ofMinutes(5), List.of());
        QrTokenService.IssuedQrToken issuedToken = qrTokenService.issue(
            QR_REFERENCE,
            456L,
            ISSUED_AT,
            ISSUED_AT.plus(Duration.ofMinutes(10))
        );

        assertThat(qrTokenService.verify(issuedToken.token(), issuedToken.expiresAt()))
            .isEqualTo(new QrTokenService.Rejected(QrTokenService.VerificationFailure.EXPIRED));
    }

    void verify_whenTokenUsesPreviousKeyBeforeVerificationEnd_returnsSignedClaims() {
        QrTokenService oldKeyService = createService("old-key", key(1), Duration.ofMinutes(5), List.of());
        QrTokenProperties.VerificationKey previousKey = verificationKey(
            "old-key",
            key(1),
            ISSUED_AT.plus(Duration.ofMinutes(5))
        );
        QrTokenService rotatedKeyService = createService(
            "current-key",
            key(2),
            Duration.ofMinutes(5),
            List.of(previousKey)
        );
        String token = issue(oldKeyService);

        assertThat(rotatedKeyService.verify(token, ISSUED_AT.plusSeconds(1)))
            .isInstanceOf(QrTokenService.Verified.class);
    }

    void verify_whenPreviousKeyVerificationHasEnded_returnsKeyFailure() {
        QrTokenService oldKeyService = createService("old-key", key(1), Duration.ofMinutes(5), List.of());
        Instant verificationEndsAt = ISSUED_AT.plus(Duration.ofMinutes(5));
        QrTokenService rotatedKeyService = createService(
            "current-key",
            key(2),
            Duration.ofMinutes(5),
            List.of(verificationKey("old-key", key(1), verificationEndsAt))
        );

        assertThat(rotatedKeyService.verify(issue(oldKeyService), verificationEndsAt))
            .isEqualTo(new QrTokenService.Rejected(QrTokenService.VerificationFailure.KEY_UNKNOWN));
    }

    void createService_whenTtlExceedsFiveMinutes_throwsIllegalStateException() {
        assertThatThrownBy(() -> createService("current-key", key(1), Duration.ofMinutes(5).plusMillis(1), List.of()))
            .isInstanceOf(IllegalStateException.class);
    }

    private QrTokenService createService(
        String activeKeyId,
        String activeKey,
        Duration tokenTtl,
        List<QrTokenProperties.VerificationKey> previousKeys
    ) {
        QrTokenProperties properties = new QrTokenProperties();
        properties.setActiveKeyId(activeKeyId);
        properties.setActiveKey(activeKey);
        properties.setTokenTtl(tokenTtl);
        properties.setPreviousKeys(previousKeys);
        return new QrTokenService(properties, Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
    }

    private String issue(QrTokenService qrTokenService) {
        return qrTokenService.issue(
            QR_REFERENCE,
            456L,
            ISSUED_AT,
            ISSUED_AT.plus(Duration.ofMinutes(10))
        ).token();
    }

    private String replaceSegment(String token, int segmentIndex, String replacement) {
        String[] segments = token.split("\\.");
        segments[segmentIndex] = replacement;
        return String.join(".", segments);
    }

    private QrTokenProperties.VerificationKey verificationKey(
        String keyId,
        String key,
        Instant verificationEndsAt
    ) {
        QrTokenProperties.VerificationKey verificationKey = new QrTokenProperties.VerificationKey();
        verificationKey.setKeyId(keyId);
        verificationKey.setKey(key);
        verificationKey.setVerificationEndsAt(verificationEndsAt);
        return verificationKey;
    }

    private String key(int value) {
        return Base64.getEncoder().encodeToString(new byte[32]).replace('A', (char) ('A' + value));
    }
}
