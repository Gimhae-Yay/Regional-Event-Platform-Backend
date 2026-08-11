package io.regionevent.regioneventbackend.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRefundRequest(
    @NotBlank String evidenceReference,
    @NotBlank String reason
) {
}
