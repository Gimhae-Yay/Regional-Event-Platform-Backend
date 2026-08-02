package io.regionevent.regioneventbackend.domain.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelContentSessionRequest(
    @NotBlank
    @Size(max = 500)
    String cancellationReason
) {
}
