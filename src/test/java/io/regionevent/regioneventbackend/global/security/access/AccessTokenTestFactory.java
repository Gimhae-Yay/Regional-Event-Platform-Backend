package io.regionevent.regioneventbackend.global.security.access;

import java.util.List;

public final class AccessTokenTestFactory {

    private static final List<AccessTokenAuthority> ALL_AUTHORITIES = List.of(AccessTokenAuthority.values());

    private AccessTokenTestFactory() {
    }

    public static String issueForAuthenticatedRequest(JwtAccessTokenService jwtAccessTokenService, Long userId) {
        return jwtAccessTokenService.issue(userId, ALL_AUTHORITIES);
    }
}
