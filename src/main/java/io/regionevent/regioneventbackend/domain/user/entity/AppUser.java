package io.regionevent.regioneventbackend.domain.user.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;


@Entity
@Table(
    name = "app_user",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_app_user_login_identifier",
        columnNames = "login_identifier"
    )
)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "login_identifier", nullable = false, length = 255)
    private String loginIdentifier;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_kind", nullable = false, length = 30, updatable = false)
    private AppUserAccountKind accountKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AppUserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    public AppUser(
        String loginIdentifier,
        String passwordHash,
        String name,
        String phone,
        AppUserStatus status
    ) {
        this(
            loginIdentifier,
            passwordHash,
            name,
            phone,
            AppUserAccountKind.ORDINARY,
            status
        );
    }

    public AppUser(
        String loginIdentifier,
        String passwordHash,
        String name,
        String phone,
        AppUserAccountKind accountKind,
        AppUserStatus status
    ) {
        this.loginIdentifier = validateRequiredText(loginIdentifier, "loginIdentifier");
        this.passwordHash = validateRequiredText(passwordHash, "passwordHash");
        this.name = validateRequiredText(name, "name");
        this.phone = validateRequiredText(phone, "phone");
        this.accountKind = validateAccountKind(accountKind);
        this.status = validateStatus(status);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public String getLoginIdentifier() {
        return loginIdentifier;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public AppUserAccountKind getAccountKind() {
        return accountKind;
    }

    public AppUserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void startWithdrawal() {
        if (status != AppUserStatus.ACTIVE) {
            throw new IllegalStateException("only active user can start withdrawal");
        }
        status = AppUserStatus.WITHDRAWING;
    }

    private static String validateRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }

    private static AppUserStatus validateStatus(AppUserStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        return status;
    }

    private static AppUserAccountKind validateAccountKind(AppUserAccountKind accountKind) {
        if (accountKind == null) {
            throw new IllegalArgumentException("accountKind must not be null");
        }
        return accountKind;
    }
}
