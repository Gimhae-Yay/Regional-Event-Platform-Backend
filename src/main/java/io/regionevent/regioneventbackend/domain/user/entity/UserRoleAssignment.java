package io.regionevent.regioneventbackend.domain.user.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import io.regionevent.regioneventbackend.domain.region.entity.Region;

@Entity
@Table(name = "user_role_assignment")
public class UserRoleAssignment {

    @EmbeddedId
    private UserRoleAssignmentId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser appUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    protected UserRoleAssignment() {
    }

    public UserRoleAssignment(AppUser appUser, UserRole role, Region region) {
        this.appUser = validateAppUser(appUser);
        validateRoleAndRegion(role, region);
        this.id = new UserRoleAssignmentId(null, role);
        this.region = region;
    }

    @PrePersist
    protected void onCreate() {
        grantedAt = Instant.now();
    }

    public UserRoleAssignmentId getId() {
        return id;
    }

    public AppUser getAppUser() {
        return appUser;
    }

    public UserRole getRole() {
        return id.getRole();
    }

    public Region getRegion() {
        return region;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    private static AppUser validateAppUser(AppUser appUser) {
        if (appUser == null) {
            throw new IllegalArgumentException("appUser must not be null");
        }
        return appUser;
    }

    private static UserRole validateRoleAndRegion(UserRole role, Region region) {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (role == UserRole.VISITOR && region != null) {
            throw new IllegalArgumentException("VISITOR must not have a region");
        }
        if (role != UserRole.VISITOR && region == null) {
            throw new IllegalArgumentException("operator roles must have a region");
        }
        return role;
    }
}
