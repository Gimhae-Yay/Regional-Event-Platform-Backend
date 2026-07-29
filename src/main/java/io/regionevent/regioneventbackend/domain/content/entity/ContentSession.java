package io.regionevent.regioneventbackend.domain.content.entity;

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
import jakarta.persistence.Version;

@Entity
@Table(name = "content_session")
public class ContentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "region_id", nullable = false)
    private Long regionId;

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

    @Column(name = "cancelled_by_user_id")
    private Long cancelledByUserId;

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

    public Long getContentId() {
        return contentId;
    }

    public Long getRegionId() {
        return regionId;
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

    public Long getCancelledByUserId() {
        return cancelledByUserId;
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
}
