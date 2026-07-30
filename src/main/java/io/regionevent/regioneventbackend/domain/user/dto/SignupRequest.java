package io.regionevent.regioneventbackend.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank
    String email,

    @NotBlank
    @Size(min = 8, max = 64)
    @Pattern(regexp = "(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).+")
    String password,

    @NotBlank
    String name,

    @NotBlank
    String phone,

    @NotBlank
    String requestedRole,

    String requestedRegionId,

    String businessInformation
) {

    @Email
    @Size(max = 254)
    public String getNormalizedEmail() {
        return normalizeText(email);
    }

    @NotBlank
    @Size(max = 50)
    public String getNormalizedName() {
        return normalizeText(name);
    }

    @Pattern(regexp = "\\d{10,11}")
    public String getNormalizedPhone() {
        return phone == null ? null : phone.replace("-", "");
    }

    private static String normalizeText(String value) {
        return value == null ? null : value.strip();
    }
}
