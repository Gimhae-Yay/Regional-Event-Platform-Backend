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
    @JoinColumn(
        name = "content_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_content_session_content_region")
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

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "reviewed_by_user_id",
        foreignKey = @ForeignKey(name = "fk_content_session_reviewed_by_user")
    )
    private AppUser reviewedByUser;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

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
        this.status = ContentSessionStatus.PENDING;
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

    public void approve(
        AppUser reviewedByUser,
        Instant reviewedAt
    ) {
        validateStatus(ContentSessionStatus.PENDING);
        AppUser validatedReviewer = requireNotNull(reviewedByUser, "reviewedByUser");
        Instant validatedReviewedAt = requireNotNull(reviewedAt, "reviewedAt");
        this.status = ContentSessionStatus.SCHEDULED;
        this.reviewedByUser = validatedReviewer;
        this.reviewedAt = validatedReviewedAt;
    }

    public void reject(
        AppUser reviewedByUser,
        Instant reviewedAt,
        String rejectReason
    ) {
        validateStatus(ContentSessionStatus.PENDING);
        AppUser validatedReviewer = requireNotNull(reviewedByUser, "reviewedByUser");
        Instant validatedReviewedAt = requireNotNull(reviewedAt, "reviewedAt");
        String validatedRejectReason = requireNotBlank(rejectReason, "rejectReason");
        this.status = ContentSessionStatus.REJECTED;
        this.reviewedByUser = validatedReviewer;
        this.reviewedAt = validatedReviewedAt;
        this.rejectReason = validatedRejectReason;
    }

    public void complete(Instant completedAt) {
        validateStatus(ContentSessionStatus.SCHEDULED);
        Instant validatedCompletedAt = requireNotNull(completedAt, "completedAt");
        this.status = ContentSessionStatus.COMPLETED;
        this.completedAt = validatedCompletedAt;
    }

    public void cancel(
        AppUser cancelledByUser,
        Instant cancelledAt,
        String cancellationReason
    ) {
        validateStatus(ContentSessionStatus.SCHEDULED);
        AppUser validatedCanceller = requireNotNull(cancelledByUser, "cancelledByUser");
        Instant validatedCancelledAt = requireNotNull(cancelledAt, "cancelledAt");
        String validatedCancellationReason = requireNotBlank(cancellationReason, "cancellationReason");
        this.status = ContentSessionStatus.CANCELLED;
        this.cancelledByUser = validatedCanceller;
        this.cancelledAt = validatedCancelledAt;
        this.cancellationReason = validatedCancellationReason;
    }

    public void releaseCapacity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        if (quantity == 0) {
            return;
        }
        int releasedCapacity = remainingCapacity + quantity;
        if (releasedCapacity > capacity) {
            throw new IllegalStateException("remainingCapacity must not exceed capacity");
        }
        remainingCapacity = releasedCapacity;
    }

    public void applyRevision(
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt,
        int capacity
    ) {
        validateStatus(ContentSessionStatus.SCHEDULED);
        Instant validatedStartsAt = requireNotNull(startsAt, "startsAt");
        Instant validatedEndsAt = requireNotNull(endsAt, "endsAt");
        Instant validatedCheckinOpenAt = requireNotNull(checkinOpenAt, "checkinOpenAt");
        Instant validatedCheckinCloseAt = requireNotNull(checkinCloseAt, "checkinCloseAt");
        validateTimeRange(
            validatedStartsAt,
            validatedEndsAt,
            validatedCheckinOpenAt,
            validatedCheckinCloseAt
        );
        this.startsAt = validatedStartsAt;
        this.endsAt = validatedEndsAt;
        this.checkinOpenAt = validatedCheckinOpenAt;
        this.checkinCloseAt = validatedCheckinCloseAt;
        this.capacity = validateCapacity(capacity);
        this.remainingCapacity = capacity;
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

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public AppUser getReviewedByUser() {
        return reviewedByUser;
    }

    public String getRejectReason() {
        return rejectReason;
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

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }

    private void validateStatus(ContentSessionStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new IllegalStateException(
                "content session status must be " + expectedStatus + " but was " + status
            );
        }
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
        if (!endsAt.isAfter(checkinCloseAt)) {
            throw new IllegalArgumentException("endsAt must be after checkinCloseAt");
        }
    }

    private static int validateCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        return capacity;
    }
}
