package io.regionevent.regioneventbackend.domain.operator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateOperatorApplicationRequest(
    @NotNull
    @Positive
    Long requestedRegionId,

    @NotBlank
    @Size(max = 2_000)
    String businessInformation
) {

    public CreateOperatorApplicationRequest {
        businessInformation = businessInformation == null ? null : businessInformation.strip();
    }
}
