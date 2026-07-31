package io.regionevent.regioneventbackend.domain.audit.service;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;

public record AuditEventActor(
    AppUser user,
    UserRole role
) {

    public AuditEventActor {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("actor must be persisted");
        }
        if (user.getStatus() != AppUserStatus.ACTIVE) {
            throw new IllegalArgumentException("actor must be active");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
    }
}
