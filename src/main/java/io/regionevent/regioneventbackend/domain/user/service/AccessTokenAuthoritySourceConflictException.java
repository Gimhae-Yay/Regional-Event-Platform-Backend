package io.regionevent.regioneventbackend.domain.user.service;

public class AccessTokenAuthoritySourceConflictException extends RuntimeException {

    public AccessTokenAuthoritySourceConflictException() {
        super("Access token authority source is inconsistent");
    }
}
