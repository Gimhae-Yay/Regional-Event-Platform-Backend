package io.regionevent.regioneventbackend.domain.coupon.dto;

import tools.jackson.databind.JsonNode;

public record UpdateCouponPolicyRequest(
    JsonNode name,
    JsonNode description,
    JsonNode discountAmount,
    JsonNode minimumPaymentAmount,
    JsonNode validDaysAfterIssue,
    JsonNode issueStartsAt,
    JsonNode issueEndsAt,
    JsonNode totalIssueLimit,
    JsonNode reason
) {
}
