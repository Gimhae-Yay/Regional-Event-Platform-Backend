package io.regionevent.regioneventbackend.domain.coupon.dto;

import jakarta.validation.constraints.NotBlank;

public record PublishCouponPolicyRequest(
    @NotBlank String reason
) {
}
