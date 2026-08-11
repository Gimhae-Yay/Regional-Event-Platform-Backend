package io.regionevent.regioneventbackend.domain.platformadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeactivateAdminAccountRequest(
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[A-Z][A-Z0-9_]*")
    String reasonCode,

    @NotBlank
    @Size(max = 500)
    String evidenceReference
) {

    public DeactivateAdminAccountRequest {
        reasonCode = normalizeText(reasonCode);
        evidenceReference = normalizeText(evidenceReference);
    }

    private static String normalizeText(String value) {
        return value == null ? null : value.strip();
    }
}
