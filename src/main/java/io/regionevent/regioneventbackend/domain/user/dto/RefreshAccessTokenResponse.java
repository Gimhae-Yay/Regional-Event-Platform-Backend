package io.regionevent.regioneventbackend.domain.user.dto;

import java.util.Objects;

public record RefreshAccessTokenResponse(
    String accessToken
) {

    public RefreshAccessTokenResponse {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
    }
}
