package io.regionevent.regioneventbackend.domain.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.regionevent.regioneventbackend.domain.user.service.RegionAdminRoleChange;

public record ChangeRegionAdminRoleRequest(
    @NotBlank
    @Pattern(regexp = "^(REGION_ADMIN|NONE)$")
    String role,
    String regionId,
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$")
    String reasonCode,
    @NotBlank
    @Size(max = 500)
    String evidenceReference
) {

    public ChangeRegionAdminRoleRequest {
        if (evidenceReference != null) {
            evidenceReference = evidenceReference.strip();
        }
    }

    @AssertTrue
    public boolean isRoleAndRegionCombinationValid() {
        if (RegionAdminRoleChange.REGION_ADMIN.name().equals(role)) {
            return regionId != null;
        }
        return !RegionAdminRoleChange.NONE.name().equals(role) || regionId == null;
    }

    public RegionAdminRoleChange toRoleChange() {
        return RegionAdminRoleChange.valueOf(role);
    }
}
