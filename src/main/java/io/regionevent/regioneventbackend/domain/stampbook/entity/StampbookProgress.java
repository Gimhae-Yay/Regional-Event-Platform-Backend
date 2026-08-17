package io.regionevent.regioneventbackend.domain.stampbook.entity;

import java.time.Instant;

import jakarta.persistence.CheckConstraint;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Entity
@Table(
    name = "stampbook_progress",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_stampbook_progress_stampbook_user",
        columnNames = {"stampbook_id", "user_id"}
    ),
    check = {
        @CheckConstraint(
            name = "ck_stampbook_progress_status",
            constraint = "status REGEXP '^(IN_PROGRESS|COMPLETED|ENDED_INCOMPLETE)$'"
        ),
        @CheckConstraint(
            name = "ck_stampbook_progress_status_completed_at",
            constraint = """
                CASE
                    WHEN status = 'IN_PROGRESS' AND completed_at IS NULL THEN 1
                    WHEN status = 'COMPLETED' AND completed_at IS NOT NULL THEN 1
                    WHEN status = 'ENDED_INCOMPLETE' AND completed_at IS NULL THEN 1
                    ELSE 0
                END = 1
                """
        )
    }
)
public class StampbookProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stampbook_progress_id")
    private Long stampbookProgressId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "stampbook_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_stampbook_progress_stampbook")
    )
    private Stampbook stampbook;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_id",
        foreignKey = @ForeignKey(name = "fk_stampbook_progress_user")
    )
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StampbookProgressStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected StampbookProgress() {
    }

    public StampbookProgress(
        Stampbook stampbook,
        AppUser user
    ) {
        this.stampbook = requireNotNull(stampbook, "stampbook");
        this.user = requireNotNull(user, "user");
        this.status = StampbookProgressStatus.IN_PROGRESS;
    }

    public Long getStampbookProgressId() {
        return stampbookProgressId;
    }

    public Stampbook getStampbook() {
        return stampbook;
    }

    public AppUser getUser() {
        return user;
    }

    public StampbookProgressStatus getStatus() {
        return status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void complete(Instant completedAt) {
        validateStatus(StampbookProgressStatus.IN_PROGRESS);
        this.completedAt = requireNotNull(completedAt, "completedAt");
        status = StampbookProgressStatus.COMPLETED;
    }

    public void endIncomplete() {
        validateStatus(StampbookProgressStatus.IN_PROGRESS);
        status = StampbookProgressStatus.ENDED_INCOMPLETE;
    }

    private void validateStatus(StampbookProgressStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new IllegalStateException("stampbook progress status cannot be changed");
        }
    }

    private static <T> T requireNotNull(
        T value,
        String fieldName
    ) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
