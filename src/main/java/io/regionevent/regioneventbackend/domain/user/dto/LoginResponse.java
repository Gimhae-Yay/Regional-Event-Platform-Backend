package io.regionevent.regioneventbackend.domain.user.dto;

import java.util.List;
import java.util.Objects;

public record LoginResponse(
    String userId,
    List<String> roles,
    String accessToken
) {

    public LoginResponse {
        roles = List.copyOf(roles);
        Objects.requireNonNull(accessToken, "accessToken must not be null");
    }
}
