package io.regionevent.regioneventbackend.domain.user.dto;

public record RefreshAccessTokenResult(
    String accessToken
) {

    @Override
    public String toString() {
        return "RefreshAccessTokenResult[redacted]";
    }
}
