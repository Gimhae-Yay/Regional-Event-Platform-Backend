package io.regionevent.regioneventbackend.domain.user.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class UserRoleAssignmentId implements Serializable {

    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private UserRole role;

    protected UserRoleAssignmentId() {
    }

    public UserRoleAssignmentId(Long userId, UserRole role) {
        this.userId = userId;
        this.role = validateRole(role);
    }

    public Long getUserId() {
        return userId;
    }

    public UserRole getRole() {
        return role;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof UserRoleAssignmentId other)) {
            return false;
        }
        return Objects.equals(userId, other.userId) && role == other.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, role);
    }

    private static UserRole validateRole(UserRole role) {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        return role;
    }
}
