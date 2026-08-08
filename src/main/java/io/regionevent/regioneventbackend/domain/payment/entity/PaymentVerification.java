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
@Table(name = "payment_verification")
public class PaymentVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_verification_id")
    private Long paymentVerificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "payment_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_payment_verification_payment")
    )
    private Payment payment;

    @Column(name = "verification_reason", nullable = false, length = 100, updatable = false)
    private String verificationReason;

    @Column(name = "observed_amount", nullable = false, updatable = false)
    private long observedAmount;

    @Column(name = "observed_currency", nullable = false, length = 3, updatable = false)
    private String observedCurrency;

    @Column(name = "observed_order_id", nullable = false, length = 255, updatable = false)
    private String observedOrderId;

    @Column(name = "external_status", nullable = false, length = 100, updatable = false)
    private String externalStatus;

    @Column(name = "internal_decision", nullable = false, length = 100, updatable = false)
    private String internalDecision;

    @Column(name = "response_hash", nullable = false, length = 255, updatable = false)
    private String responseHash;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private Instant verifiedAt;

    protected PaymentVerification() {
    }

    public PaymentVerification(
        Payment payment,
        String verificationReason,
        long observedAmount,
        String observedCurrency,
        String observedOrderId,
        String externalStatus,
        String internalDecision,
        String responseHash,
        Instant verifiedAt
    ) {
        this.payment = requireNotNull(payment, "payment");
        this.verificationReason = requireNotBlank(verificationReason, "verificationReason");
        if (observedAmount < 0) {
            throw new IllegalArgumentException("observedAmount must not be negative");
        }
        this.observedAmount = observedAmount;
        this.observedCurrency = requireCurrency(observedCurrency);
        this.observedOrderId = requireNotBlank(observedOrderId, "observedOrderId");
        this.externalStatus = requireNotBlank(externalStatus, "externalStatus");
        this.internalDecision = requireNotBlank(internalDecision, "internalDecision");
        this.responseHash = requireNotBlank(responseHash, "responseHash");
        this.verifiedAt = requireNotNull(verifiedAt, "verifiedAt");
    }

    public Long getPaymentVerificationId() {
        return paymentVerificationId;
    }

    public Payment getPayment() {
        return payment;
    }

    public String getVerificationReason() {
        return verificationReason;
    }

    public long getObservedAmount() {
        return observedAmount;
    }

    public String getObservedCurrency() {
        return observedCurrency;
    }

    public String getObservedOrderId() {
        return observedOrderId;
    }

    public String getExternalStatus() {
        return externalStatus;
    }

    public String getInternalDecision() {
        return internalDecision;
    }

    public String getResponseHash() {
        return responseHash;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    private static String requireCurrency(String value) {
        if (value == null || value.length() != 3) {
            throw new IllegalArgumentException("observedCurrency must be a three-letter code");
        }
        return value;
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
