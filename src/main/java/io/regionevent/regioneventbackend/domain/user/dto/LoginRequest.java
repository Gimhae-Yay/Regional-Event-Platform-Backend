package io.regionevent.regioneventbackend.domain.user.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank
    @Email
    @Size(max = 254)
    String email,

    @NotBlank
    String password
) {

    public LoginRequest {
        email = normalizeEmail(email);
    }

    private static String normalizeEmail(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
