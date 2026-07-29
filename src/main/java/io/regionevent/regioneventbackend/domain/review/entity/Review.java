package io.regionevent.regioneventbackend.domain.review.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;

@Entity
@Table(
    name = "review",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_review_visit",
        columnNames = "visit_id"
    ),
    check = {
        @CheckConstraint(
            name = "ck_review_status",
            constraint = "status REGEXP '^(PUBLISHED|DELETED)$'"
        ),
        @CheckConstraint(
            name = "ck_review_state",
            constraint = """
                (status = 'PUBLISHED'
                    AND rating IS NOT NULL
                    AND rating BETWEEN 1 AND 5
                    AND review_text IS NOT NULL
                    AND CHAR_LENGTH(review_text) BETWEEN 1 AND 2000
                    AND TRIM(review_text) <> ''
                    AND deleted_at IS NULL)
                OR (status = 'DELETED'
                    AND deleted_at IS NOT NULL
                    AND ((rating IS NOT NULL
                            AND rating BETWEEN 1 AND 5
                            AND review_text IS NOT NULL
                            AND CHAR_LENGTH(review_text) BETWEEN 1 AND 2000
                            AND TRIM(review_text) <> '')
                        OR (rating IS NULL AND review_text IS NULL)))
                """
        )
    }
)
public class Review {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final int MAX_REVIEW_TEXT_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "region_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Region region;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "visit_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Visit visit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_id",
        foreignKey = @ForeignKey(name = "fk_review_user")
    )
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "content_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Content content;

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "review_text", columnDefinition = "TEXT")
    private String reviewText;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private ReviewStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "author_unlinked_at")
    private Instant authorUnlinkedAt;

    protected Review() {
    }

    public Review(
        Region region,
        Visit visit,
        AppUser user,
        Content content,
        Integer rating,
        String reviewText,
        ReviewStatus status,
        Instant deletedAt
    ) {
        this.region = requireNotNull(region, "region");
        this.visit = requireNotNull(visit, "visit");
        this.user = requireNotNull(user, "user");
        this.content = requireNotNull(content, "content");
        validateRelations();
        this.rating = rating;
        this.reviewText = reviewText;
        this.status = requireNotNull(status, "status");
        this.deletedAt = deletedAt;
        validateStateFields();
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

    public void unlinkAuthor(Instant unlinkedAt) {
        if (user == null || authorUnlinkedAt != null) {
            throw new IllegalStateException("review author is already unlinked");
        }
        user = null;
        authorUnlinkedAt = requireNotNull(unlinkedAt, "unlinkedAt");
    }

    public Long getReviewId() {
        return reviewId;
    }

    public Region getRegion() {
        return region;
    }

    public Visit getVisit() {
        return visit;
    }

    public AppUser getUser() {
        return user;
    }

    public Content getContent() {
        return content;
    }

    public Integer getRating() {
        return rating;
    }

    public String getReviewText() {
        return reviewText;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getAuthorUnlinkedAt() {
        return authorUnlinkedAt;
    }

    private void validateRelations() {
        validateSameEntity(visit.getRegion(), region, "region");
        validateSameEntity(visit.getContent(), content, "content");
        validateSameEntity(visit.getUser(), user, "user");
    }

    private void validateStateFields() {
        if (status == ReviewStatus.PUBLISHED) {
            validateReviewOriginal();
            if (deletedAt != null) {
                throw new IllegalArgumentException("deletedAt must be null for published review");
            }
            return;
        }
        if (deletedAt == null) {
            throw new IllegalArgumentException("deletedAt must not be null for deleted review");
        }
        if ((rating == null) != (reviewText == null)) {
            throw new IllegalArgumentException("review original fields must be both present or absent");
        }
        if (rating != null) {
            validateReviewOriginal();
        }
    }

    private void validateReviewOriginal() {
        if (rating == null || rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
        if (reviewText == null || reviewText.isBlank() || reviewText.length() > MAX_REVIEW_TEXT_LENGTH) {
            throw new IllegalArgumentException("reviewText must be between 1 and 2000 non-blank characters");
        }
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static void validateSameEntity(
        Region expected,
        Region actual,
        String fieldName
    ) {
        if (expected == actual) {
            return;
        }
        Long expectedId = expected.getRegionId();
        Long actualId = actual.getRegionId();
        if (expectedId == null || !expectedId.equals(actualId)) {
            throw new IllegalArgumentException(fieldName + " must match visit");
        }
    }

    private static void validateSameEntity(
        Content expected,
        Content actual,
        String fieldName
    ) {
        if (expected == actual) {
            return;
        }
        Long expectedId = expected.getContentId();
        Long actualId = actual.getContentId();
        if (expectedId == null || !expectedId.equals(actualId)) {
            throw new IllegalArgumentException(fieldName + " must match visit");
        }
    }

    private static void validateSameEntity(
        AppUser expected,
        AppUser actual,
        String fieldName
    ) {
        if (expected == actual) {
            return;
        }
        if (expected == null || actual == null) {
            throw new IllegalArgumentException(fieldName + " must match visit");
        }
        Long expectedId = expected.getUserId();
        Long actualId = actual.getUserId();
        if (expectedId == null || !expectedId.equals(actualId)) {
            throw new IllegalArgumentException(fieldName + " must match visit");
        }
    }
}
