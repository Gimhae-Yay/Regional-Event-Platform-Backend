package io.regionevent.regioneventbackend.domain.audit.service;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;

public final class AuditEventActor {

    private final AppUser appUser;
    private final String platformAdminRoleName;
    private final UserRoleAssignment roleAssignment;

    public AuditEventActor(UserRoleAssignment roleAssignment) {
        if (roleAssignment == null
            || roleAssignment.getRoleAssignmentId() == null
            || roleAssignment.getAppUser() == null
            || roleAssignment.getAppUser().getUserId() == null) {
            throw new IllegalArgumentException("roleAssignment must be persisted");
        }
        if (roleAssignment.getAppUser().getStatus() != AppUserStatus.ACTIVE) {
            throw new IllegalArgumentException("actor must be active");
        }

        this.appUser = roleAssignment.getAppUser();
        this.platformAdminRoleName = null;
        this.roleAssignment = roleAssignment;
    }

    public AuditEventActor(PlatformAdminAssignment platformAdminAssignment) {
        if (platformAdminAssignment == null
            || platformAdminAssignment.getPlatformAdminAssignmentId() == null
            || platformAdminAssignment.getAppUser() == null
            || platformAdminAssignment.getAppUser().getUserId() == null) {
            throw new IllegalArgumentException("platformAdminAssignment must be persisted");
        }
        if (!platformAdminAssignment.isActive()
            || platformAdminAssignment.getAppUser().getStatus() != AppUserStatus.ACTIVE
            || platformAdminAssignment.getAppUser().getAccountKind()
                != AppUserAccountKind.PRIVILEGED) {
            throw new IllegalArgumentException("platform admin actor must be active and privileged");
        }

        this.appUser = platformAdminAssignment.getAppUser();
        this.platformAdminRoleName = platformAdminAssignment.getGrade().name();
        this.roleAssignment = null;
    }

    public AppUser getAppUser() {
        return appUser;
    }

    public String getRoleName() {
        return roleAssignment == null ? platformAdminRoleName : roleAssignment.getRole().name();
    }

    public UserRole getRole() {
        return roleAssignment == null ? null : roleAssignment.getRole();
    }

    public UserRoleAssignment roleAssignment() {
        return roleAssignment;
    }
}
