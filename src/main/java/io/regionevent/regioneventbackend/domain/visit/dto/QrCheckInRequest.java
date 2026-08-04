package io.regionevent.regioneventbackend.domain.visit.dto;

import jakarta.validation.constraints.NotBlank;

public record QrCheckInRequest(
    @NotBlank String qrToken
) {
}
