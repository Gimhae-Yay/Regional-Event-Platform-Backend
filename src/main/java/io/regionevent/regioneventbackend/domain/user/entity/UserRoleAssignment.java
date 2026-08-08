package io.regionevent.regioneventbackend.domain.user.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import io.regionevent.regioneventbackend.domain.region.entity.Region;

@Entity
@Table(name = "user_role_assignment")
public class UserRoleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_assignment_id")
    private Long roleAssignmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser appUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UserRoleAssignmentStatus status;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason_code", length = 100)
    private String revokeReasonCode;

    protected UserRoleAssignment() {
    }

    public UserRoleAssignment(AppUser appUser, UserRole role, Region region) {
        this.appUser = validateAppUser(appUser);
        validateRoleAndRegion(role, region);
        this.role = role;
        this.region = region;
        this.status = UserRoleAssignmentStatus.ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        grantedAt = Instant.now();
    }

    public Long getRoleAssignmentId() {
        return roleAssignmentId;
    }

    public AppUser getAppUser() {
        return appUser;
    }

    public UserRole getRole() {
        return role;
    }

    public Region getRegion() {
        return region;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public UserRoleAssignmentStatus getStatus() {
        return status;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokeReasonCode() {
        return revokeReasonCode;
    }

    public void revoke(Instant revokedAt, String revokeReasonCode) {
        if (status != UserRoleAssignmentStatus.ACTIVE) {
            throw new IllegalStateException("only active role assignment can be revoked");
        }
        if (revokedAt == null) {
            throw new IllegalArgumentException("revokedAt must not be null");
        }
        if (revokeReasonCode == null || revokeReasonCode.isBlank()) {
            throw new IllegalArgumentException("revokeReasonCode must not be null or blank");
        }
        this.status = UserRoleAssignmentStatus.REVOKED;
        this.revokedAt = revokedAt;
        this.revokeReasonCode = revokeReasonCode;
    }

    public void unlinkAppUser() {
        if (status != UserRoleAssignmentStatus.REVOKED) {
            throw new IllegalStateException("only revoked role assignment can be unlinked");
        }
        appUser = null;
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
