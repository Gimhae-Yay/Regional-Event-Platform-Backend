package io.regionevent.regioneventbackend.domain.content.entity;

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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Entity
@Table(
    name = "session_revision",
    check = {
        @CheckConstraint(
            name = "ck_session_revision_time_range",
            constraint = """
                starts_at < ends_at
                AND checkin_open_at < checkin_close_at
                AND ends_at > checkin_close_at
                """
        ),
        @CheckConstraint(
            name = "ck_session_revision_capacity",
            constraint = "capacity > 0"
        ),
        @CheckConstraint(
            name = "ck_session_revision_status",
            constraint = "status REGEXP '^(PENDING|APPROVED|REJECTED)$'"
        ),
        @CheckConstraint(
            name = "ck_session_revision_review_state",
            constraint = """
                (status = 'PENDING'
                    AND reviewed_at IS NULL
                    AND reviewed_by_user_id IS NULL
                    AND reject_reason IS NULL)
                OR (status = 'APPROVED'
                    AND reviewed_at IS NOT NULL
                    AND reviewed_by_user_id IS NOT NULL
                    AND reject_reason IS NULL)
                OR (status = 'REJECTED'
                    AND reviewed_at IS NOT NULL
                    AND reviewed_by_user_id IS NOT NULL
                    AND reject_reason IS NOT NULL
                    AND TRIM(reject_reason) <> '')
                """
        )
    }
)
public class SessionRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_revision_id")
    private Long sessionRevisionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "content_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "region_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "target_session_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private ContentSession targetSession;

    @Column(name = "base_session_version", nullable = false)
    private int baseSessionVersion;

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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private SessionRevisionStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "requested_by_user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_session_revision_requested_by_user")
    )
    private AppUser requestedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "reviewed_by_user_id",
        foreignKey = @ForeignKey(name = "fk_session_revision_reviewed_by_user")
    )
    private AppUser reviewedBy;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SessionRevision() {
    }

    public SessionRevision(
        Content content,
        Region region,
        ContentSession targetSession,
        int baseSessionVersion,
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt,
        int capacity,
        SessionRevisionStatus status,
        AppUser requestedBy,
        Instant submittedAt,
        Instant reviewedAt,
        AppUser reviewedBy,
        String rejectReason
    ) {
        this.content = requireNotNull(content, "content");
        this.region = requireNotNull(region, "region");
        this.targetSession = requireNotNull(targetSession, "targetSession");
        validateTargetSession();
        this.baseSessionVersion = baseSessionVersion;
        this.startsAt = requireNotNull(startsAt, "startsAt");
        this.endsAt = requireNotNull(endsAt, "endsAt");
        this.checkinOpenAt = requireNotNull(checkinOpenAt, "checkinOpenAt");
        this.checkinCloseAt = requireNotNull(checkinCloseAt, "checkinCloseAt");
        validateTimeRange();
        this.capacity = validateCapacity(capacity);
        this.status = requireNotNull(status, "status");
        this.requestedBy = requireNotNull(requestedBy, "requestedBy");
        this.submittedAt = requireNotNull(submittedAt, "submittedAt");
        this.reviewedAt = reviewedAt;
        this.reviewedBy = reviewedBy;
        this.rejectReason = rejectReason;
        validateReviewState();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Long getSessionRevisionId() {
        return sessionRevisionId;
    }

    public Content getContent() {
        return content;
    }

    public Region getRegion() {
        return region;
    }

    public ContentSession getTargetSession() {
        return targetSession;
    }

    public int getBaseSessionVersion() {
        return baseSessionVersion;
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

    public SessionRevisionStatus getStatus() {
        return status;
    }

    public AppUser getRequestedBy() {
        return requestedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public AppUser getReviewedBy() {
        return reviewedBy;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void approve(AppUser reviewer, Instant reviewedAt) {
        if (status != SessionRevisionStatus.PENDING) {
            throw new IllegalStateException("session revision must be pending");
        }
        this.status = SessionRevisionStatus.APPROVED;
        this.reviewedBy = requireNotNull(reviewer, "reviewer");
        this.reviewedAt = requireNotNull(reviewedAt, "reviewedAt");
    }

    private void validateTargetSession() {
        if (targetSession.getStatus() != ContentSessionStatus.SCHEDULED) {
            throw new IllegalArgumentException("targetSession must be scheduled");
        }
        validateSameEntity(targetSession.getContent(), content, "content", "targetSession");
        validateSameEntity(targetSession.getRegion(), region, "region", "targetSession");
    }

    private void validateTimeRange() {
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

    private void validateReviewState() {
        boolean isPending = status == SessionRevisionStatus.PENDING;
        boolean isApproved = status == SessionRevisionStatus.APPROVED;
        boolean isRejected = status == SessionRevisionStatus.REJECTED;

        if ((isPending && (reviewedAt != null || reviewedBy != null || rejectReason != null))
            || (isApproved && (reviewedAt == null || reviewedBy == null || rejectReason != null))
            || (isRejected && (reviewedAt == null || reviewedBy == null || rejectReason == null || rejectReason.isBlank()))) {
            throw new IllegalArgumentException("review fields do not match session revision status");
        }
    }

    private static int validateCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        return capacity;
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static void validateSameEntity(
        Content expected,
        Content actual,
        String fieldName,
        String sourceName
    ) {
        if (expected == actual) {
            return;
        }
        Long expectedId = expected.getContentId();
        Long actualId = actual.getContentId();
        if (expectedId == null || !expectedId.equals(actualId)) {
            throw new IllegalArgumentException(fieldName + " must match " + sourceName);
        }
    }

    private static void validateSameEntity(
        Region expected,
        Region actual,
        String fieldName,
        String sourceName
    ) {
        if (expected == actual) {
            return;
        }
        Long expectedId = expected.getRegionId();
        Long actualId = actual.getRegionId();
        if (expectedId == null || !expectedId.equals(actualId)) {
            throw new IllegalArgumentException(fieldName + " must match " + sourceName);
        }
    }
}
