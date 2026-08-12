package io.regionevent.regioneventbackend.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveRefundFailureRequest(
    @NotBlank String confirmedStatus,
    @NotBlank String evidenceReference,
    @NotBlank String reason
) {
}
