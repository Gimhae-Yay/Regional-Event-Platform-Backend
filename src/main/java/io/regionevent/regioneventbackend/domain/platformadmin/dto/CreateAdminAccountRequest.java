package io.regionevent.regioneventbackend.domain.platformadmin.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAdminAccountRequest(
    @NotBlank
    @Email
    @Size(max = 254)
    String email,

    @NotBlank
    @Size(min = 8, max = 64)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9])[!-~]{8,64}$")
    String password,

    @NotBlank
    @Size(max = 50)
    String name,

    @NotBlank
    @Pattern(regexp = "\\d{10,11}")
    String phone,

    @NotBlank
    @Pattern(regexp = "SUPER_ADMIN|PLATFORM_ADMIN")
    String grade,

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[A-Z][A-Z0-9_]*")
    String reasonCode,

    @NotBlank
    @Size(max = 500)
    String evidenceReference
) {

    public CreateAdminAccountRequest {
        email = normalizeEmail(email);
        name = normalizeText(name);
        phone = normalizePhone(phone);
        reasonCode = normalizeText(reasonCode);
        evidenceReference = normalizeText(evidenceReference);
    }

    private static String normalizeEmail(String value) {
        String normalizedEmail = normalizeText(value);
        return normalizedEmail == null ? null : normalizedEmail.toLowerCase(Locale.ROOT);
    }

    private static String normalizePhone(String value) {
        return value == null ? null : value.replace("-", "");
    }

    private static String normalizeText(String value) {
        return value == null ? null : value.strip();
    }
}
