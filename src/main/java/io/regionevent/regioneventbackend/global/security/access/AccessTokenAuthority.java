package io.regionevent.regioneventbackend.global.security.access;

import java.util.Arrays;

public enum AccessTokenAuthority {
    VISITOR("ROLE_VISITOR"),
    OPERATOR("ROLE_OPERATOR"),
    REGION_ADMIN("ROLE_REGION_ADMIN"),
    PLATFORM_ADMIN("ROLE_PLATFORM_ADMIN"),
    SUPER_ADMIN("ROLE_SUPER_ADMIN");

    private final String claimValue;

    AccessTokenAuthority(String claimValue) {
        this.claimValue = claimValue;
    }

    public String claimValue() {
        return claimValue;
    }

    public static AccessTokenAuthority fromClaimValue(String claimValue) {
        return Arrays.stream(values())
            .filter(authority -> authority.claimValue.equals(claimValue))
            .findFirst()
            .orElseThrow(InvalidAccessTokenException::new);
    }
}
