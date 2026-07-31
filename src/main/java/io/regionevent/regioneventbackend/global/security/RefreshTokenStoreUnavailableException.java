package io.regionevent.regioneventbackend.global.security;

public class RefreshTokenStoreUnavailableException extends RuntimeException {

    public RefreshTokenStoreUnavailableException(Throwable cause) {
        super(cause);
    }
}
