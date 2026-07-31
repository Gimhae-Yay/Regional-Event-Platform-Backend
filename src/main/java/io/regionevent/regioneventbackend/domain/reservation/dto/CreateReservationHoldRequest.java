package io.regionevent.regioneventbackend.domain.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateReservationHoldRequest(
    @NotBlank
    String sessionId,

    @NotNull
    @Positive
    Integer quantity
) {
}
