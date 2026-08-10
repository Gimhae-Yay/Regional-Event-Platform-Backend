package io.regionevent.regioneventbackend.domain.payment.dto;

import tools.jackson.databind.JsonNode;

public record CreatePaymentRequest(JsonNode couponId) {
}
