package io.regionevent.regioneventbackend.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRefundRequest(
    @NotBlank @Size(max = 500) String evidenceReference,
    @NotBlank @Size(max = 500) String reason
) {
}
