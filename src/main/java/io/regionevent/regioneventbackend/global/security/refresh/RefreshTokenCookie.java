package io.regionevent.regioneventbackend.global.security.refresh;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

public final class RefreshTokenCookie {

    private static final String NAME = "refreshToken";
    private static final String PATH = "/api/v1/auth";

    private RefreshTokenCookie() {
    }

    public static String create(String token, Duration maxAge) {
        return ResponseCookie.from(NAME, token)
            .maxAge(maxAge)
            .path(PATH)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .build()
            .toString();
    }

    public static String expire() {
        return create("", Duration.ZERO);
    }
}
