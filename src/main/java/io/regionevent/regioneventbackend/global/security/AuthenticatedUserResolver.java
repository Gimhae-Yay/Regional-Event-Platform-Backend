package io.regionevent.regioneventbackend.global.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Component
public class AuthenticatedUserResolver {

    public Long resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, exception);
        }
    }
}
