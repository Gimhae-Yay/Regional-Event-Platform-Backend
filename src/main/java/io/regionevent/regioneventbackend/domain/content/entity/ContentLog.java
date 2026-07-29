package io.regionevent.regioneventbackend.domain.content.entity;

import java.time.Instant;
import java.util.EnumSet;

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

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Entity
@Table(name = "content_log")
public class ContentLog {

    private static final EnumSet<ContentLogStatus> REASON_REQUIRED_STATUSES = EnumSet.of(
        ContentLogStatus.REJECTED,
        ContentLogStatus.SUSPENDED,
        ContentLogStatus.WITHDRAWN,
        ContentLogStatus.DELETED
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "content_id",
        nullable = false,
        updatable = false,
        foreignKey = @ForeignKey(name = "fk_content_log_content")
    )
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "actor_id",
        foreignKey = @ForeignKey(name = "fk_content_log_actor")
    )
    private AppUser actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 30)
    private ContentLogStatus status;

    @Column(name = "reason", updatable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "date", nullable = false, updatable = false)
    private Instant date;

    protected ContentLog() {
    }

    public ContentLog(
        Content content,
        AppUser actor,
        ContentLogStatus status,
        String reason,
        Instant date
    ) {
        this.content = requireNotNull(content, "content");
        this.actor = actor;
        this.status = requireNotNull(status, "status");
        this.reason = validateReason(status, reason);
        this.date = requireNotNull(date, "date");
    }

    public void unlinkActor() {
        actor = null;
    }

    public Long getId() {
        return id;
    }

    public Content getContent() {
        return content;
    }

    public AppUser getActor() {
        return actor;
    }

    public ContentLogStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getDate() {
        return date;
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static String validateReason(ContentLogStatus status, String reason) {
        if (REASON_REQUIRED_STATUSES.contains(status) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("reason must not be null or blank for " + status);
        }
        return reason;
    }
}
