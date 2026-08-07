package io.regionevent.regioneventbackend.domain.user.dto;

import java.util.List;

public record LoginResponse(
    String userId,
    List<String> roles
) {

    public LoginResponse {
        roles = List.copyOf(roles);
    }
}
