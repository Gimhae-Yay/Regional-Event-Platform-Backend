package io.regionevent.regioneventbackend.domain.audit.service;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;

public record AuditEventActor(
    UserRoleAssignment roleAssignment
) {

    public AuditEventActor {
        if (roleAssignment == null
            || roleAssignment.getRoleAssignmentId() == null
            || roleAssignment.getAppUser() == null
            || roleAssignment.getAppUser().getUserId() == null) {
            throw new IllegalArgumentException("roleAssignment must be persisted");
        }
        if (roleAssignment.getAppUser().getStatus() != AppUserStatus.ACTIVE) {
            throw new IllegalArgumentException("actor must be active");
        }
    }

    public AppUser getAppUser() {
        return roleAssignment.getAppUser();
    }

    public UserRole getRole() {
        return roleAssignment.getRole();
    }
}
