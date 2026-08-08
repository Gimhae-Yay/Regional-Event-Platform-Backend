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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "refund",
    uniqueConstraints = @UniqueConstraint(name = "uk_refund_payment", columnNames = "payment_id"),
    check = {
        @CheckConstraint(name = "ck_refund_amount", constraint = "amount >= 0"),
        @CheckConstraint(
            name = "ck_refund_status",
            constraint = "status REGEXP '^(REQUESTED|PROCESSING|SUCCEEDED|FAILED|DISCREPANT)$'"
        )
    }
)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_id")
    private Long refundId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "payment_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_refund_payment")
    )
    private Payment payment;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RefundStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Refund() {
    }

    public Refund(
        Payment payment,
        long amount,
        Instant requestedAt
    ) {
        this.payment = requireNotNull(payment, "payment");
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        this.amount = amount;
        this.status = RefundStatus.REQUESTED;
        this.requestedAt = requireNotNull(requestedAt, "requestedAt");
    }

    public Long getRefundId() {
        return refundId;
    }

    public Payment getPayment() {
        return payment;
    }

    public long getAmount() {
        return amount;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
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
