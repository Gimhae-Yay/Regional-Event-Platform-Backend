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
import jakarta.persistence.Table;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Entity
@Table(name = "coupon_policy_update_history")
public class CouponPolicyUpdateHistory {

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "actor_user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_coupon_policy_update_history_actor")
    )
    private AppUser actor;

    @Column(name = "previous_name", nullable = false, length = 255, updatable = false)
    private String previousName;

    @Column(name = "previous_description", length = 1_000, updatable = false)
    private String previousDescription;

    @Column(name = "previous_discount_amount", nullable = false, updatable = false)
    private long previousDiscountAmount;

    @Column(name = "previous_minimum_payment_amount", nullable = false, updatable = false)
    private long previousMinimumPaymentAmount;

    @Column(name = "previous_valid_days", nullable = false, updatable = false)
    private int previousValidDays;

    @Column(name = "previous_issue_starts_at", nullable = false, updatable = false)
    private Instant previousIssueStartsAt;

    @Column(name = "previous_issue_ends_at", nullable = false, updatable = false)
    private Instant previousIssueEndsAt;

    @Column(name = "previous_total_issue_limit", updatable = false)
    private Long previousTotalIssueLimit;

    @Column(name = "next_name", nullable = false, length = 255, updatable = false)
    private String nextName;

    @Column(name = "next_description", length = 1_000, updatable = false)
    private String nextDescription;

    @Column(name = "next_discount_amount", nullable = false, updatable = false)
    private long nextDiscountAmount;

    @Column(name = "next_minimum_payment_amount", nullable = false, updatable = false)
    private long nextMinimumPaymentAmount;

    @Column(name = "next_valid_days", nullable = false, updatable = false)
    private int nextValidDays;

    @Column(name = "next_issue_starts_at", nullable = false, updatable = false)
    private Instant nextIssueStartsAt;

    @Column(name = "next_issue_ends_at", nullable = false, updatable = false)
    private Instant nextIssueEndsAt;

    @Column(name = "next_total_issue_limit", updatable = false)
    private Long nextTotalIssueLimit;

    @Column(name = "reason", nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected CouponPolicyUpdateHistory() {
    }

    public CouponPolicyUpdateHistory(
        CouponPolicy couponPolicy,
        AppUser actor,
        Snapshot previousValues,
        Snapshot nextValues,
        String reason,
        Instant occurredAt
    ) {
        this.couponPolicy = requireNotNull(couponPolicy, "couponPolicy");
        this.actor = requireNotNull(actor, "actor");
        applyPreviousValues(requireNotNull(previousValues, "previousValues"));
        applyNextValues(requireNotNull(nextValues, "nextValues"));
        this.reason = requireNotBlank(reason, "reason");
        this.occurredAt = requireNotNull(occurredAt, "occurredAt");
    }

    public static Snapshot snapshotOf(CouponPolicy couponPolicy) {
        return new Snapshot(
            couponPolicy.getName(),
            couponPolicy.getDescription(),
            couponPolicy.getDiscountAmount(),
            couponPolicy.getMinimumPaymentAmount(),
            couponPolicy.getValidDays(),
            couponPolicy.getIssueStartsAt(),
            couponPolicy.getIssueEndsAt(),
            couponPolicy.getTotalIssueLimit()
        );
    }

    public Long getCouponPolicyUpdateHistoryId() {
        return couponPolicyUpdateHistoryId;
    }

    public CouponPolicy getCouponPolicy() {
        return couponPolicy;
    }

    public AppUser getActor() {
        return actor;
    }

    public String getPreviousName() {
        return previousName;
    }

    public String getNextName() {
        return nextName;
    }

    public long getPreviousDiscountAmount() {
        return previousDiscountAmount;
    }

    public long getNextDiscountAmount() {
        return nextDiscountAmount;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    private void applyPreviousValues(Snapshot values) {
        previousName = values.name();
        previousDescription = values.description();
        previousDiscountAmount = values.discountAmount();
        previousMinimumPaymentAmount = values.minimumPaymentAmount();
        previousValidDays = values.validDays();
        previousIssueStartsAt = values.issueStartsAt();
        previousIssueEndsAt = values.issueEndsAt();
        previousTotalIssueLimit = values.totalIssueLimit();
    }

    private void applyNextValues(Snapshot values) {
        nextName = values.name();
        nextDescription = values.description();
        nextDiscountAmount = values.discountAmount();
        nextMinimumPaymentAmount = values.minimumPaymentAmount();
        nextValidDays = values.validDays();
        nextIssueStartsAt = values.issueStartsAt();
        nextIssueEndsAt = values.issueEndsAt();
        nextTotalIssueLimit = values.totalIssueLimit();
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
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public record Snapshot(
        String name,
        String description,
        long discountAmount,
        long minimumPaymentAmount,
        int validDays,
        Instant issueStartsAt,
        Instant issueEndsAt,
        Long totalIssueLimit
    ) {
    }
}
