package io.regionevent.regioneventbackend.domain.content.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectContentRequest(
    @NotBlank String reason
) {
}
