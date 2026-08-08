package io.regionevent.regioneventbackend.domain.payment.entity;

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

@Entity
@Table(
    name = "refund_attempt",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_refund_attempt_refund_no",
        columnNames = {"refund_id", "attempt_no"}
    ),
    check = {
        @CheckConstraint(name = "ck_refund_attempt_no", constraint = "attempt_no BETWEEN 1 AND 3"),
        @CheckConstraint(
            name = "ck_refund_attempt_initiator_kind",
            constraint = "initiator_kind REGEXP '^(SYSTEM|SUPER_ADMIN|PLATFORM_ADMIN)$'"
        ),
        @CheckConstraint(
            name = "ck_refund_attempt_outcome_kind",
            constraint = "outcome_kind REGEXP '^(PENDING|RESPONDED|NO_RESPONSE)$'"
        ),
        @CheckConstraint(
            name = "ck_refund_attempt_failure_reason_code",
            constraint = """
                failure_reason_code IS NULL
                OR failure_reason_code REGEXP '^(TIMEOUT|CONNECTION|NETWORK|PROCESS_INTERRUPTED|UNKNOWN)$'
                """
        ),
        @CheckConstraint(
            name = "ck_refund_attempt_outcome_values",
            constraint = """
                (outcome_kind = 'PENDING'
                    AND failure_reason_code IS NULL
                    AND external_status IS NULL
                    AND result_hash IS NULL)
                OR (outcome_kind = 'RESPONDED'
                    AND failure_reason_code IS NULL
                    AND external_status IS NOT NULL
                    AND result_hash IS NOT NULL)
                OR (outcome_kind = 'NO_RESPONSE'
                    AND failure_reason_code IS NOT NULL
                    AND external_status IS NULL
                    AND result_hash IS NULL)
                """
        )
    }
)
public class RefundAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_attempt_id")
    private Long refundAttemptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "refund_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_refund_attempt_refund")
    )
    private Refund refund;

    @Column(name = "attempt_no", nullable = false, updatable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "initiator_kind", nullable = false, length = 30, updatable = false)
    private RefundAttemptInitiatorKind initiatorKind;

    @Column(name = "portone_cancellation_id", length = 255)
    private String portoneCancellationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome_kind", nullable = false, length = 30)
    private RefundAttemptOutcomeKind outcomeKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason_code", length = 100)
    private RefundFailureReasonCode failureReasonCode;

    @Column(name = "external_status", length = 100)
    private String externalStatus;

    @Column(name = "result_hash", length = 255)
    private String resultHash;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    protected RefundAttempt() {
    }

    public RefundAttempt(
        Refund refund,
        int attemptNo,
        RefundAttemptInitiatorKind initiatorKind,
        Instant attemptedAt
    ) {
        this.refund = requireNotNull(refund, "refund");
        if (attemptNo < 1 || attemptNo > 3) {
            throw new IllegalArgumentException("attemptNo must be between 1 and 3");
        }
        this.attemptNo = attemptNo;
        this.initiatorKind = requireNotNull(initiatorKind, "initiatorKind");
        this.outcomeKind = RefundAttemptOutcomeKind.PENDING;
        this.attemptedAt = requireNotNull(attemptedAt, "attemptedAt");
    }

    public Long getRefundAttemptId() {
        return refundAttemptId;
    }

    public Refund getRefund() {
        return refund;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public RefundAttemptInitiatorKind getInitiatorKind() {
        return initiatorKind;
    }

    public String getPortoneCancellationId() {
        return portoneCancellationId;
    }

    public RefundAttemptOutcomeKind getOutcomeKind() {
        return outcomeKind;
    }

    public RefundFailureReasonCode getFailureReasonCode() {
        return failureReasonCode;
    }

    public String getExternalStatus() {
        return externalStatus;
    }

    public String getResultHash() {
        return resultHash;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
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
