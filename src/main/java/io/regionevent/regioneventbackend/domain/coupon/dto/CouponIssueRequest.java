package io.regionevent.regioneventbackend.domain.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;

public record CouponIssueRequest(
    @NotNull CouponIssuanceType issueSourceType,
    @NotBlank String sourceId
) {
}
