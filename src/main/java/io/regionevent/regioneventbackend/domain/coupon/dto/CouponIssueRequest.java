package io.regionevent.regioneventbackend.domain.coupon.dto;

import jakarta.validation.constraints.NotBlank;

import tools.jackson.databind.annotation.JsonDeserialize;

public record CouponIssueRequest(
    @NotBlank @JsonDeserialize(using = StrictStringDeserializer.class) String issueSourceType,
    @NotBlank @JsonDeserialize(using = StrictStringDeserializer.class) String sourceId
) {
}
