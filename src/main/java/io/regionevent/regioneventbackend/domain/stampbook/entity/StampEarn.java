package io.regionevent.regioneventbackend.domain.stampbook.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;

@Entity
@Table(
    name = "stamp_earn",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_stamp_earn_progress_visit",
            columnNames = {"stampbook_progress_id", "visit_id"}
        ),
        @UniqueConstraint(
            name = "uk_stamp_earn_progress_content",
            columnNames = {"stampbook_progress_id", "content_id"}
        )
    }
)
public class StampEarn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stamp_earn_id")
    private Long stampEarnId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "stampbook_progress_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_stamp_earn_progress")
    )
    private StampbookProgress stampbookProgress;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "visit_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_stamp_earn_visit")
    )
    private Visit visit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "content_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_stamp_earn_content")
    )
    private Content content;

    @Column(name = "earned_at", nullable = false, updatable = false)
    private Instant earnedAt;

    protected StampEarn() {
    }

    public StampEarn(
        StampbookProgress stampbookProgress,
        Visit visit,
        Content content,
        Instant earnedAt
    ) {
        this.stampbookProgress = requireNotNull(stampbookProgress, "stampbookProgress");
        this.visit = requireNotNull(visit, "visit");
        this.content = requireNotNull(content, "content");
        this.earnedAt = requireNotNull(earnedAt, "earnedAt");
        validateRelations();
    }

    public Long getStampEarnId() {
        return stampEarnId;
    }

    public StampbookProgress getStampbookProgress() {
        return stampbookProgress;
    }

    public Visit getVisit() {
        return visit;
    }

    public Content getContent() {
        return content;
    }

    public Instant getEarnedAt() {
        return earnedAt;
    }

    private void validateRelations() {
        validateSameEntity(stampbookProgress.getUser(), visit.getUser(), "user");
        validateSameEntity(visit.getContent(), content, "content");
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

    private static void validateSameEntity(
        AppUser expected,
        AppUser actual,
        String fieldName
    ) {
        if (expected == actual) {
            return;
        }
        if (expected == null || actual == null) {
            throw new IllegalArgumentException(fieldName + " must match stampbook progress and visit");
        }
        Long expectedId = expected.getUserId();
        Long actualId = actual.getUserId();
        if (expectedId == null || !expectedId.equals(actualId)) {
            throw new IllegalArgumentException(fieldName + " must match stampbook progress and visit");
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
}
