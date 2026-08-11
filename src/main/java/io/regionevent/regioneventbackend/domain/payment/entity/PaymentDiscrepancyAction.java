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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_discrepancy_action")
public class PaymentDiscrepancyAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_discrepancy_action_id")
    private Long paymentDiscrepancyActionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "payment_discrepancy_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_payment_discrepancy_action_discrepancy")
    )
    private PaymentDiscrepancy paymentDiscrepancy;

    @Column(name = "action_type", nullable = false, length = 100, updatable = false)
    private String actionType;

    @Column(name = "evidence_reference", length = 500, updatable = false)
    private String evidenceReference;

    @Column(name = "reason_code", nullable = false, length = 100, updatable = false)
    private String reasonCode;

    @Column(name = "result_code", nullable = false, length = 100, updatable = false)
    private String resultCode;

    @Column(name = "acted_at", nullable = false, updatable = false)
    private Instant actedAt;

    protected PaymentDiscrepancyAction() {
    }

    public PaymentDiscrepancyAction(
        PaymentDiscrepancy paymentDiscrepancy,
        String actionType,
        String evidenceReference,
        String reasonCode,
        String resultCode,
        Instant actedAt
    ) {
        this.paymentDiscrepancy = requireNotNull(paymentDiscrepancy, "paymentDiscrepancy");
        this.actionType = requireNotBlank(actionType, "actionType");
        this.evidenceReference = evidenceReference;
        this.reasonCode = requireNotBlank(reasonCode, "reasonCode");
        this.resultCode = requireNotBlank(resultCode, "resultCode");
        this.actedAt = requireNotNull(actedAt, "actedAt");
    }

    public Long getPaymentDiscrepancyActionId() {
        return paymentDiscrepancyActionId;
    }

    public PaymentDiscrepancy getPaymentDiscrepancy() {
        return paymentDiscrepancy;
    }

    public String getActionType() {
        return actionType;
    }

    public String getEvidenceReference() {
        return evidenceReference;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getResultCode() {
        return resultCode;
    }

    public Instant getActedAt() {
        return actedAt;
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
