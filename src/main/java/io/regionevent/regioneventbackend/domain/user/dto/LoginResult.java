package io.regionevent.regioneventbackend.domain.user.dto;

import java.util.Objects;

public record LoginResult(
    LoginResponse response,
    String accessToken,
    String refreshToken
) {

    public LoginResult {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
    }
}
