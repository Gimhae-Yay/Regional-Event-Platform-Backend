package io.regionevent.regioneventbackend.domain.region.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateRegionStatusRequest(
    @NotNull
    Boolean isPublic,

    @NotBlank
    @Pattern(
        regexp = "^(REGION_LAUNCH|REGION_REOPEN|REGION_PREPARATION|ADMINISTRATIVE_REORGANIZATION)$"
    )
    String reasonCode,

    @NotBlank
    @Size(max = 500)
    String evidenceReference
) {

    public UpdateRegionStatusRequest {
        reasonCode = normalize(reasonCode);
        evidenceReference = normalize(evidenceReference);
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip();
    }
}
