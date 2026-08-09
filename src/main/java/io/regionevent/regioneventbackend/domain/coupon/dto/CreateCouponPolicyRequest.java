package io.regionevent.regioneventbackend.domain.coupon.dto;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateCouponPolicyRequest(
    @NotBlank @Size(max = 20) String contentId,
    @NotBlank @Size(max = 255) String name,
    @Size(max = 1000) String description,
    @NotBlank String issueSourceType,
    @NotNull @Positive Long discountAmount,
    @NotNull @PositiveOrZero Long minimumPaymentAmount,
    @NotNull @Min(1) @Max(365) Integer validDaysAfterIssue,
    @NotNull Instant issueStartsAt,
    @NotNull Instant issueEndsAt,
    @Positive Long totalIssueLimit
) {
}
