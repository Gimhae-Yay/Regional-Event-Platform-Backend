package io.regionevent.regioneventbackend.domain.content.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawContentRevisionRequest(
    @NotBlank String reason
) {
}
