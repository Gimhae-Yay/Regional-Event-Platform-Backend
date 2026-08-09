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

@Entity
@Table(name = "platform_admin_assignment")
public class PlatformAdminAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "platform_admin_assignment_id")
    private Long platformAdminAssignmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser appUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false, length = 30)
    private PlatformAdminGrade grade;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PlatformAdminAssignmentStatus status;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "inactivated_at")
    private Instant inactivatedAt;

    @Column(name = "inactive_reason_code", length = 255)
    private String inactiveReasonCode;

    protected PlatformAdminAssignment() {
    }

    public PlatformAdminAssignment(
        AppUser appUser,
        PlatformAdminGrade grade
    ) {
        this.appUser = validatePrivilegedUser(appUser);
        this.grade = validateGrade(grade);
        this.status = PlatformAdminAssignmentStatus.ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        grantedAt = Instant.now();
    }

    public Long getPlatformAdminAssignmentId() {
        return platformAdminAssignmentId;
    }

    public AppUser getAppUser() {
        return appUser;
    }

    public PlatformAdminGrade getGrade() {
        return grade;
    }

    public PlatformAdminAssignmentStatus getStatus() {
        return status;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getInactivatedAt() {
        return inactivatedAt;
    }

    public String getInactiveReasonCode() {
        return inactiveReasonCode;
    }

    public boolean isActive() {
        return status == PlatformAdminAssignmentStatus.ACTIVE;
    }

    public void inactivate(
        Instant inactivatedAt,
        String inactiveReasonCode
    ) {
        if (!isActive()) {
            throw new IllegalStateException("only active platform admin assignment can be inactivated");
        }
        if (inactivatedAt == null) {
            throw new IllegalArgumentException("inactivatedAt must not be null");
        }
        if (inactiveReasonCode == null || inactiveReasonCode.isBlank()) {
            throw new IllegalArgumentException("inactiveReasonCode must not be null or blank");
        }
        status = PlatformAdminAssignmentStatus.INACTIVE;
        this.inactivatedAt = inactivatedAt;
        this.inactiveReasonCode = inactiveReasonCode;
    }

    private static AppUser validatePrivilegedUser(AppUser appUser) {
        if (appUser == null) {
            throw new IllegalArgumentException("appUser must not be null");
        }
        if (appUser.getAccountKind() != AppUserAccountKind.PRIVILEGED) {
            throw new IllegalArgumentException("platform admin assignment requires a privileged user");
        }
        return appUser;
    }

    private static PlatformAdminGrade validateGrade(PlatformAdminGrade grade) {
        if (grade == null) {
            throw new IllegalArgumentException("grade must not be null");
        }
        return grade;
    }
}
