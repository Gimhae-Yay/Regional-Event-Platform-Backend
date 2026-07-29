package io.regionevent.regioneventbackend.domain.content.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Entity
@Table(name = "content_session")
public class ContentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
        foreignKey = @ForeignKey(name = "fk_content_session_content_region"),
        value = {
            @JoinColumn(name = "content_id", referencedColumnName = "content_id", nullable = false),
            @JoinColumn(
                name = "region_id",
                referencedColumnName = "region_id",
                nullable = false,
                insertable = false,
                updatable = false
            )
        }
    )
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "region_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_content_session_region")
    )
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ContentSessionStatus status;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "checkin_open_at", nullable = false)
    private Instant checkinOpenAt;

    @Column(name = "checkin_close_at", nullable = false)
    private Instant checkinCloseAt;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "remaining_capacity", nullable = false)
    private int remainingCapacity;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "cancelled_by_user_id",
        foreignKey = @ForeignKey(name = "fk_content_session_cancelled_by_user")
    )
    private AppUser cancelledByUser;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContentSession() {
    }

    public ContentSession(
        Content content,
        Region region,
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt,
        int capacity
    ) {
        this.content = requireNotNull(content, "content");
        this.region = requireNotNull(region, "region");
        this.status = ContentSessionStatus.SCHEDULED;
        this.startsAt = requireNotNull(startsAt, "startsAt");
        this.endsAt = requireNotNull(endsAt, "endsAt");
        this.checkinOpenAt = requireNotNull(checkinOpenAt, "checkinOpenAt");
        this.checkinCloseAt = requireNotNull(checkinCloseAt, "checkinCloseAt");
        validateTimeRange(startsAt, endsAt, checkinOpenAt, checkinCloseAt);
        this.capacity = validateCapacity(capacity);
        this.remainingCapacity = capacity;
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

    public Long getSessionId() {
        return sessionId;
    }

    public Content getContent() {
        return content;
    }

    public Region getRegion() {
        return region;
    }

    public ContentSessionStatus getStatus() {
        return status;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Instant getCheckinOpenAt() {
        return checkinOpenAt;
    }

    public Instant getCheckinCloseAt() {
        return checkinCloseAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getRemainingCapacity() {
        return remainingCapacity;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public AppUser getCancelledByUser() {
        return cancelledByUser;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static void validateTimeRange(
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt
    ) {
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("startsAt must be before endsAt");
        }
        if (!checkinOpenAt.isBefore(checkinCloseAt)) {
            throw new IllegalArgumentException("checkinOpenAt must be before checkinCloseAt");
        }
        if (endsAt.isAfter(checkinCloseAt)) {
            throw new IllegalArgumentException("endsAt must be before or equal to checkinCloseAt");
        }
    }

    private static int validateCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        return capacity;
    }
}
