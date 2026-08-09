package io.regionevent.regioneventbackend.domain.coupon.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public class CouponIssuanceHasher {

    public String hashVisitIssue(Long couponPolicyId, Long userId) {
        return hash("couponPolicyId=%d;userId=%d;sourceType=VISIT".formatted(couponPolicyId, userId));
    }

    public String hashStampbookCompletionIssue(Long couponPolicyId, Long stampbookRewardGrantId) {
        return hash(
            "couponPolicyId=%d;stampbookRewardGrantId=%d;sourceType=STAMPBOOK_COMPLETION".formatted(
                couponPolicyId,
                stampbookRewardGrantId
            )
        );
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
