package io.regionevent.regioneventbackend.domain.operator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectOperatorApplicationRequest(
    @NotBlank
    @Size(max = 2_000)
    String rejectedReason
) {

    public RejectOperatorApplicationRequest {
        rejectedReason = rejectedReason == null ? null : rejectedReason.strip();
    }
}
