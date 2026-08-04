package io.regionevent.regioneventbackend.domain.visit.dto;

import jakarta.validation.constraints.NotBlank;

public record ManualCheckInRequest(
    @NotBlank String reservationNo,
    @NotBlank String reason
) {
}
