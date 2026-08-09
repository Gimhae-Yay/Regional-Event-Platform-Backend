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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "payment_webhook",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_payment_webhook_provider_event",
        columnNames = "provider_event_id"
    )
)
public class PaymentWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_webhook_id")
    private Long paymentWebhookId;

    @Column(name = "provider_event_id", nullable = false, length = 255, updatable = false)
    private String providerEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "payment_id",
        foreignKey = @ForeignKey(name = "fk_payment_webhook_payment")
    )
    private Payment payment;

    @Column(name = "authentication_result", nullable = false, length = 100, updatable = false)
    private String authenticationResult;

    @Column(name = "processing_result", nullable = false, length = 100, updatable = false)
    private String processingResult;

    @Column(name = "payload_hash", nullable = false, length = 255, updatable = false)
    private String payloadHash;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected PaymentWebhook() {
    }

    public PaymentWebhook(
        String providerEventId,
        Payment payment,
        String authenticationResult,
        String processingResult,
        String payloadHash,
        Instant receivedAt
    ) {
        this.providerEventId = requireNotBlank(providerEventId, "providerEventId");
        this.payment = payment;
        this.authenticationResult = requireNotBlank(authenticationResult, "authenticationResult");
        this.processingResult = requireNotBlank(processingResult, "processingResult");
        this.payloadHash = requireNotBlank(payloadHash, "payloadHash");
        this.receivedAt = requireNotNull(receivedAt, "receivedAt");
    }

    public Long getPaymentWebhookId() {
        return paymentWebhookId;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public Payment getPayment() {
        return payment;
    }

    public String getAuthenticationResult() {
        return authenticationResult;
    }

    public String getProcessingResult() {
        return processingResult;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public Instant getReceivedAt() {
        return receivedAt;
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
