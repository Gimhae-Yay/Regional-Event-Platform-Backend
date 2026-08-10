package io.regionevent.regioneventbackend.domain.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EndCouponPolicyRequest(
    @NotBlank
    @Size(max = 500)
    String reason
) {

    public EndCouponPolicyRequest {
        reason = reason == null ? null : reason.strip();
    }
}
