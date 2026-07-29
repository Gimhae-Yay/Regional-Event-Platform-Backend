package io.regionevent.regioneventbackend.domain.audit.entity;

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
import jakarta.persistence.Table;

import io.regionevent.regioneventbackend.domain.region.entity.Region;

@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_event_id")
    private Long auditEventId;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "region_id",
        foreignKey = @ForeignKey(name = "fk_audit_event_region")
    )
    private Region region;

    @Column(name = "target_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AuditEventTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "previous_state", length = 30)
    private String previousState;

    @Column(name = "next_state", length = 30)
    private String nextState;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 10)
    private AuditEventResult result;

    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "actor_kind", nullable = false, length = 30)
    private String actorKind;

    @Column(name = "actor_role", length = 30)
    private String actorRole;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    public AuditEvent(
        String requestId,
        Region region,
        AuditEventTargetType targetType,
        Long targetId,
        String previousState,
        String nextState,
        AuditEventResult result,
        String reasonCode,
        String actorKind,
        String actorRole,
        Instant occurredAt
    ) {
        this.requestId = requireNotBlank(requestId, "requestId");
        this.region = region;
        this.targetType = requireNotNull(targetType, "targetType");
        this.targetId = targetId;
        this.previousState = previousState;
        this.nextState = nextState;
        this.result = requireNotNull(result, "result");
        this.reasonCode = reasonCode;
        this.actorKind = requireNotBlank(actorKind, "actorKind");
        this.actorRole = actorRole;
        this.occurredAt = requireNotNull(occurredAt, "occurredAt");
    }

    public Long getAuditEventId() {
        return auditEventId;
    }

    public String getRequestId() {
        return requestId;
    }

    public Region getRegion() {
        return region;
    }

    public AuditEventTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getPreviousState() {
        return previousState;
    }

    public String getNextState() {
        return nextState;
    }

    public AuditEventResult getResult() {
        return result;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getActorKind() {
        return actorKind;
    }

    public String getActorRole() {
        return actorRole;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
