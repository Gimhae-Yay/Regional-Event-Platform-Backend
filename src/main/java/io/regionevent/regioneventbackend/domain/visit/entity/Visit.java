package io.regionevent.regioneventbackend.domain.visit.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Entity
@Table(
    name = "visit",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_visit_reservation",
            columnNames = "reservation_id"
        ),
        @UniqueConstraint(
            name = "uk_visit_visit_content_region",
            columnNames = {"visit_id", "content_id", "region_id"}
        )
    },
    check = @CheckConstraint(
        name = "ck_visit_checkin_method",
        constraint = "checkin_method REGEXP '^(QR|RESERVATION_NUMBER)$'"
    )
)
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "visit_id")
    private Long visitId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "region_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Region region;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "reservation_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_id",
        foreignKey = @ForeignKey(name = "fk_visit_user")
    )
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "content_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "session_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private ContentSession contentSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "checked_in_by_user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_visit_checked_in_by_user")
    )
    private AppUser checkedInByUser;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "checkin_method", nullable = false, length = 30)
    private CheckinMethod checkinMethod;

    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt;

    @Column(name = "author_unlinked_at")
    private Instant authorUnlinkedAt;

    protected Visit() {
    }

    public Visit(
        Region region,
        Reservation reservation,
        AppUser user,
        Content content,
        ContentSession contentSession,
        AppUser checkedInByUser,
        CheckinMethod checkinMethod,
        Instant checkedAt
    ) {
        this.region = requireNotNull(region, "region");
        this.reservation = requireNotNull(reservation, "reservation");
        this.user = user;
        this.content = requireNotNull(content, "content");
        this.contentSession = requireNotNull(contentSession, "contentSession");
        this.checkedInByUser = requireNotNull(checkedInByUser, "checkedInByUser");
        this.checkinMethod = requireNotNull(checkinMethod, "checkinMethod");
        this.checkedAt = requireNotNull(checkedAt, "checkedAt");
        validateRelations();
    }

    public void unlinkAuthor(Instant unlinkedAt) {
        if (user == null || authorUnlinkedAt != null) {
            throw new IllegalStateException("visit author is already unlinked");
        }
        user = null;
        authorUnlinkedAt = requireNotNull(unlinkedAt, "unlinkedAt");
    }

    public Long getVisitId() {
        return visitId;
    }

    public Region getRegion() {
        return region;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public AppUser getUser() {
        return user;
    }

    public Content getContent() {
        return content;
    }

    public ContentSession getContentSession() {
        return contentSession;
    }

    public AppUser getCheckedInByUser() {
        return checkedInByUser;
    }

    public CheckinMethod getCheckinMethod() {
        return checkinMethod;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public Instant getAuthorUnlinkedAt() {
        return authorUnlinkedAt;
    }

    private void validateRelations() {
        validateSameEntity(reservation.getRegion(), region, "region");
        validateSameEntity(reservation.getContentSession(), contentSession, "contentSession");
        validateSameEntity(contentSession.getRegion(), region, "region");
        validateSameEntity(contentSession.getContent(), content, "content");
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
            throw new IllegalArgumentException(fieldName + " must match reservation and contentSession");
        }
    }

    private static void validateSameEntity(
        ContentSession expected,
        ContentSession actual,
        String fieldName
    ) {
        if (expected == actual) {
            return;
        }
        Long expectedId = expected.getSessionId();
        Long actualId = actual.getSessionId();
        if (expectedId == null || !expectedId.equals(actualId)) {
            throw new IllegalArgumentException(fieldName + " must match reservation");
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
            throw new IllegalArgumentException(fieldName + " must match contentSession");
        }
    }
}
