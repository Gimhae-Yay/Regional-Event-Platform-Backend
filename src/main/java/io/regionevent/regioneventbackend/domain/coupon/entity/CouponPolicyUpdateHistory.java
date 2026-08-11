package io.regionevent.regioneventbackend.domain.coupon.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;

@Entity
@Table(
    name = "coupon_policy_update_history",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_coupon_policy_update_history_audit_event",
        columnNames = "audit_event_id"
    )
)
public class CouponPolicyUpdateHistory {

    private static final String USER_ACTOR_KIND = "USER";
    private static final String OPERATOR_ACTOR_ROLE = UserRole.OPERATOR.name();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_policy_update_history_id")
    private Long couponPolicyUpdateHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "coupon_policy_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_coupon_policy_update_history_policy")
    )
    private CouponPolicy couponPolicy;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "audit_event_id",
        foreignKey = @ForeignKey(name = "fk_coupon_policy_update_history_audit")
    )
    private AuditEvent auditEvent;

    @Column(name = "actor_kind", nullable = false, length = 30, updatable = false)
    private String actorKind;

    @Column(name = "actor_role", nullable = false, length = 30, updatable = false)
    private String actorRole;

    @Column(name = "reason", nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "request_id", nullable = false, length = 36, updatable = false)
    private String requestId;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "previous_name", nullable = false, length = 255, updatable = false)
    private String previousName;

    @Column(name = "next_name", nullable = false, length = 255, updatable = false)
    private String nextName;

    @Column(name = "previous_description", length = 1000, updatable = false)
    private String previousDescription;

    @Column(name = "next_description", length = 1000, updatable = false)
    private String nextDescription;

    @Column(name = "previous_discount_amount", nullable = false, updatable = false)
    private long previousDiscountAmount;

    @Column(name = "next_discount_amount", nullable = false, updatable = false)
    private long nextDiscountAmount;

    @Column(name = "previous_minimum_payment_amount", nullable = false, updatable = false)
    private long previousMinimumPaymentAmount;

    @Column(name = "next_minimum_payment_amount", nullable = false, updatable = false)
    private long nextMinimumPaymentAmount;

    @Column(name = "previous_valid_days", nullable = false, updatable = false)
    private int previousValidDays;

    @Column(name = "next_valid_days", nullable = false, updatable = false)
    private int nextValidDays;

    @Column(name = "previous_issue_starts_at", nullable = false, updatable = false)
    private Instant previousIssueStartsAt;

    @Column(name = "next_issue_starts_at", nullable = false, updatable = false)
    private Instant nextIssueStartsAt;

    @Column(name = "previous_issue_ends_at", nullable = false, updatable = false)
    private Instant previousIssueEndsAt;

    @Column(name = "next_issue_ends_at", nullable = false, updatable = false)
    private Instant nextIssueEndsAt;

    @Column(name = "previous_total_issue_limit", updatable = false)
    private Long previousTotalIssueLimit;

    @Column(name = "next_total_issue_limit", updatable = false)
    private Long nextTotalIssueLimit;

    protected CouponPolicyUpdateHistory() {
    }

    public CouponPolicyUpdateHistory(
        CouponPolicy couponPolicy,
        AuditEvent auditEvent,
        String actorRole,
        String reason,
        String requestId,
        Instant updatedAt,
        CouponPolicyUpdateSnapshot previous,
        CouponPolicyUpdateSnapshot next
    ) {
        this.couponPolicy = requireNotNull(couponPolicy, "couponPolicy");
        this.auditEvent = requireNotNull(auditEvent, "auditEvent");
        this.actorKind = USER_ACTOR_KIND;
        this.actorRole = validateActorRole(actorRole);
        this.reason = requireNotBlank(reason, "reason").strip();
        this.requestId = requireNotBlank(requestId, "requestId");
        this.updatedAt = requireNotNull(updatedAt, "updatedAt");
        copySnapshots(requireNotNull(previous, "previous"), requireNotNull(next, "next"));
        validateAuditEvent();
    }

    public Long getCouponPolicyUpdateHistoryId() {
        return couponPolicyUpdateHistoryId;
    }

    public CouponPolicy getCouponPolicy() {
        return couponPolicy;
    }

    public AuditEvent getAuditEvent() {
        return auditEvent;
    }

    public String getActorKind() {
        return actorKind;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getReason() {
        return reason;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getPreviousName() {
        return previousName;
    }

    public String getNextName() {
        return nextName;
    }

    public String getPreviousDescription() {
        return previousDescription;
    }

    public String getNextDescription() {
        return nextDescription;
    }

    public long getPreviousDiscountAmount() {
        return previousDiscountAmount;
    }

    public long getNextDiscountAmount() {
        return nextDiscountAmount;
    }

    public long getPreviousMinimumPaymentAmount() {
        return previousMinimumPaymentAmount;
    }

    public long getNextMinimumPaymentAmount() {
        return nextMinimumPaymentAmount;
    }

    public int getPreviousValidDays() {
        return previousValidDays;
    }

    public int getNextValidDays() {
        return nextValidDays;
    }

    public Instant getPreviousIssueStartsAt() {
        return previousIssueStartsAt;
    }

    public Instant getNextIssueStartsAt() {
        return nextIssueStartsAt;
    }

    public Instant getPreviousIssueEndsAt() {
        return previousIssueEndsAt;
    }

    public Instant getNextIssueEndsAt() {
        return nextIssueEndsAt;
    }

    public Long getPreviousTotalIssueLimit() {
        return previousTotalIssueLimit;
    }

    public Long getNextTotalIssueLimit() {
        return nextTotalIssueLimit;
    }

    private void copySnapshots(
        CouponPolicyUpdateSnapshot previous,
        CouponPolicyUpdateSnapshot next
    ) {
        previousName = previous.name();
        nextName = next.name();
        previousDescription = previous.description();
        nextDescription = next.description();
        previousDiscountAmount = previous.discountAmount();
        nextDiscountAmount = next.discountAmount();
        previousMinimumPaymentAmount = previous.minimumPaymentAmount();
        nextMinimumPaymentAmount = next.minimumPaymentAmount();
        previousValidDays = previous.validDays();
        nextValidDays = next.validDays();
        previousIssueStartsAt = previous.issueStartsAt();
        nextIssueStartsAt = next.issueStartsAt();
        previousIssueEndsAt = previous.issueEndsAt();
        nextIssueEndsAt = next.issueEndsAt();
        previousTotalIssueLimit = previous.totalIssueLimit();
        nextTotalIssueLimit = next.totalIssueLimit();
    }

    private void validateAuditEvent() {
        if (auditEvent.getResult() != AuditEventResult.SUCCESS
            || auditEvent.getTargetType() != AuditEventTargetType.COUPON_POLICY
            || !couponPolicy.getCouponPolicyId().equals(auditEvent.getTargetId())
            || !CouponPolicyStatus.DRAFT.name().equals(auditEvent.getPreviousState())
            || !CouponPolicyStatus.DRAFT.name().equals(auditEvent.getNextState())
            || !actorKind.equals(auditEvent.getActorKind())
            || !actorRole.equals(auditEvent.getActorRole())
            || !reason.equals(auditEvent.getReason())
            || !requestId.equals(auditEvent.getRequestId())
            || !updatedAt.equals(auditEvent.getOccurredAt())) {
            throw new IllegalArgumentException("auditEvent must match coupon policy update history");
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

    private static String requireNotBlank(
        String value,
        String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }

    private static String validateActorRole(String actorRole) {
        String validatedActorRole = requireNotBlank(actorRole, "actorRole");
        if (!OPERATOR_ACTOR_ROLE.equals(validatedActorRole)) {
            throw new IllegalArgumentException("actorRole must be OPERATOR");
        }
        return validatedActorRole;
    }
}
