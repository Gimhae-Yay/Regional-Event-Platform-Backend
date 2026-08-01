package io.regionevent.regioneventbackend.domain.user.dto;

import java.time.Duration;

public record RefreshAccessTokenResult(
    String accessToken,
    String refreshToken,
    Duration refreshTokenMaxAge
) {

    @Override
    public String toString() {
        return "RefreshAccessTokenResult[redacted]";
    }
}
