package io.regionevent.regioneventbackend.domain.reservation.entity;

import java.time.Instant;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Entity
@Table(
    name = "capacity_hold",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_capacity_hold_hold_session_region",
        columnNames = {"hold_id", "session_id", "region_id"}
    ),
    check = {
        @CheckConstraint(
            name = "ck_capacity_hold_status",
            constraint = "status REGEXP '^(ACTIVE|CONSUMED|EXPIRED|INVALIDATED)$'"
        ),
        @CheckConstraint(
            name = "ck_capacity_hold_quantity",
            constraint = "quantity > 0"
        ),
        @CheckConstraint(
            name = "ck_capacity_hold_terminal",
            constraint = """
                CASE
                    WHEN status = 'ACTIVE'
                        AND terminal_at IS NULL
                        AND capacity_released_at IS NULL THEN 1
                    WHEN status = 'CONSUMED'
                        AND terminal_at IS NOT NULL
                        AND capacity_released_at IS NULL THEN 1
                    WHEN status = 'EXPIRED'
                        AND terminal_at IS NOT NULL
                        AND capacity_released_at IS NOT NULL THEN 1
                    WHEN status = 'INVALIDATED'
                        AND terminal_at IS NOT NULL
                        AND capacity_released_at IS NOT NULL THEN 1
                    ELSE 0
                END = 1
                """
        )
    }
)
public class CapacityHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hold_id")
    private Long holdId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "region_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "session_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private ContentSession contentSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_id",
        foreignKey = @ForeignKey(name = "fk_capacity_hold_user")
    )
    private AppUser user;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private CapacityHoldStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "terminal_at")
    private Instant terminalAt;

    @Column(name = "invalidation_reason", columnDefinition = "TEXT")
    private String invalidationReason;

    @Column(name = "capacity_released_at")
    private Instant capacityReleasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CapacityHold() {
    }

    public CapacityHold(
        Region region,
        ContentSession contentSession,
        AppUser user,
        int quantity,
        CapacityHoldStatus status,
        Instant expiresAt,
        Instant terminalAt,
        String invalidationReason,
        Instant capacityReleasedAt
    ) {
        this.region = requireNotNull(region, "region");
        this.contentSession = requireNotNull(contentSession, "contentSession");
        validateSessionRegion(region, contentSession);
        this.user = user;
        this.quantity = validateQuantity(quantity);
        this.status = requireNotNull(status, "status");
        this.expiresAt = requireNotNull(expiresAt, "expiresAt");
        this.terminalAt = terminalAt;
        this.invalidationReason = invalidationReason;
        this.capacityReleasedAt = capacityReleasedAt;
        validateTerminalFields();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Long getHoldId() {
        return holdId;
    }

    public Region getRegion() {
        return region;
    }

    public ContentSession getContentSession() {
        return contentSession;
    }

    public AppUser getUser() {
        return user;
    }

    public int getQuantity() {
        return quantity;
    }

    public CapacityHoldStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getTerminalAt() {
        return terminalAt;
    }

    public String getInvalidationReason() {
        return invalidationReason;
    }

    public Instant getCapacityReleasedAt() {
        return capacityReleasedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static int validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        return quantity;
    }

    private static void validateSessionRegion(
        Region region,
        ContentSession contentSession
    ) {
        Region sessionRegion = contentSession.getRegion();
        if (sessionRegion == region) {
            return;
        }

        Long regionId = region.getRegionId();
        Long sessionRegionId = sessionRegion.getRegionId();
        if (regionId == null || !regionId.equals(sessionRegionId)) {
            throw new IllegalArgumentException("region must match contentSession region");
        }
    }

    private void validateTerminalFields() {
        boolean isActive = status == CapacityHoldStatus.ACTIVE;
        boolean isConsumed = status == CapacityHoldStatus.CONSUMED;
        boolean isReleased = status == CapacityHoldStatus.EXPIRED || status == CapacityHoldStatus.INVALIDATED;

        if ((isActive && (terminalAt != null || capacityReleasedAt != null))
            || (isConsumed && (terminalAt == null || capacityReleasedAt != null))
            || (isReleased && (terminalAt == null || capacityReleasedAt == null))) {
            throw new IllegalArgumentException("terminal fields do not match capacity hold status");
        }
    }
}
