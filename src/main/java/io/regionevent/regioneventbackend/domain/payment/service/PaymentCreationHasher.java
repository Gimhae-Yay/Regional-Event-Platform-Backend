package io.regionevent.regioneventbackend.domain.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public class PaymentCreationHasher {

    public String hashIdempotencyKey(String idempotencyKey) {
        return hash(idempotencyKey);
    }

    public String hashRequest(long holdId, Long couponId) {
        return hash("holdId=" + holdId + "&couponId=" + (couponId == null ? "null" : couponId));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
