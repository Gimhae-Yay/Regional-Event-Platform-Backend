package io.regionevent.regioneventbackend.domain.user.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank
    @Email
    @Size(max = 254)
    String email,

    @NotBlank
    @Size(min = 8, max = 64)
    @Pattern(regexp = "(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).+")
    String password,

    @NotBlank
    @Size(max = 50)
    String name,

    @NotBlank
    @Pattern(regexp = "\\d{10,11}")
    String phone,

    @NotBlank
    String requestedRole,

    String requestedRegionId,

    String businessInformation
) {

    public SignupRequest {
        email = normalizeEmail(email);
        name = normalizeText(name);
        phone = normalizePhone(phone);
        businessInformation = normalizeText(businessInformation);
    }

    private static String normalizeText(String value) {
        return value == null ? null : value.strip();
    }

    private static String normalizeEmail(String value) {
        String normalizedEmail = normalizeText(value);
        return normalizedEmail == null ? null : normalizedEmail.toLowerCase(Locale.ROOT);
    }

    private static String normalizePhone(String value) {
        return value == null ? null : value.replace("-", "");
    }
}
