package io.regionevent.regioneventbackend.domain.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Entity
@Table(name = "audit_event_actor_link")
public class AuditEventActorLink {

    @Id
    @Column(name = "audit_event_id")
    private Long auditEventId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(
        name = "audit_event_id",
        foreignKey = @ForeignKey(name = "fk_audit_event_actor_link_audit_event")
    )
    private AuditEvent auditEvent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_audit_event_actor_link_app_user")
    )
    private AppUser actor;

    protected AuditEventActorLink() {
    }

    public AuditEventActorLink(
        AuditEvent auditEvent,
        AppUser actor
    ) {
        this.auditEvent = requireNotNull(auditEvent, "auditEvent");
        this.actor = requireNotNull(actor, "actor");
    }

    public Long getAuditEventId() {
        return auditEventId;
    }

    public AuditEvent getAuditEvent() {
        return auditEvent;
    }

    public AppUser getActor() {
        return actor;
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
