package io.regionevent.regioneventbackend.domain.visit.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public class CheckInHasher {

    public String hashIdempotencyKey(String idempotencyKey) {
        return sha256Hex(idempotencyKey);
    }

    public String hashQrRequest(String qrToken) {
        String tokenDigest = sha256Hex(qrToken);
        return sha256Hex("method=QR;tokenDigest=" + tokenDigest);
    }

    public String hashManualRequest(Long reservationId, ManualCheckInReason reason) {
        return sha256Hex("reservationId=%d;method=RESERVATION_NUMBER;reason=%s".formatted(
            reservationId,
            reason.name()
        ));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}
