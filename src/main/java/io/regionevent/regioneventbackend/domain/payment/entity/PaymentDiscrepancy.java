package io.regionevent.regioneventbackend.domain.payment.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "payment_discrepancy",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_payment_discrepancy_payment",
        columnNames = "payment_id"
    )
)
public class PaymentDiscrepancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_discrepancy_id")
    private Long paymentDiscrepancyId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "payment_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_payment_discrepancy_payment")
    )
    private Payment payment;

    @Column(name = "discrepancy_type", nullable = false, length = 100, updatable = false)
    private String discrepancyType;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    protected PaymentDiscrepancy() {
    }

    public PaymentDiscrepancy(
        Payment payment,
        String discrepancyType,
        String status,
        Instant detectedAt
    ) {
        this.payment = requireNotNull(payment, "payment");
        this.discrepancyType = requireNotBlank(discrepancyType, "discrepancyType");
        this.status = requireNotBlank(status, "status");
        this.detectedAt = requireNotNull(detectedAt, "detectedAt");
    }

    public Long getPaymentDiscrepancyId() {
        return paymentDiscrepancyId;
    }

    public Payment getPayment() {
        return payment;
    }

    public String getDiscrepancyType() {
        return discrepancyType;
    }

    public String getStatus() {
        return status;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void requestRefund() {
        if (!"OPEN".equals(status)) {
            throw new IllegalStateException("only open discrepancy can request refund");
        }
        status = "REFUND_REQUESTED";
    }

    public void resolveNoIssue() {
        if (!"OPEN".equals(status)) {
            throw new IllegalStateException("only open discrepancy can be resolved without issue");
        }
        status = "RESOLVED_NO_ISSUE";
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
}
