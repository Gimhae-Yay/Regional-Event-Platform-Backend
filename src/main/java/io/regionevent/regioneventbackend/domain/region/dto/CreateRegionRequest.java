package io.regionevent.regioneventbackend.domain.region.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRegionRequest(
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*$")
    String regionCode,

    @NotBlank
    @Size(max = 100)
    String name,

    @NotBlank
    @Pattern(
        regexp = "^(PILOT_REGION_ADDITION|SERVICE_AREA_EXPANSION|ADMINISTRATIVE_REORGANIZATION)$"
    )
    String reasonCode,

    @NotBlank
    @Size(max = 500)
    String evidenceReference
) {

    public CreateRegionRequest {
        regionCode = normalize(regionCode);
        name = normalize(name);
        reasonCode = normalize(reasonCode);
        evidenceReference = normalize(evidenceReference);
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip();
    }
}
