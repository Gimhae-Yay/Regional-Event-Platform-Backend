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

import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;

@Entity
@Table(
    name = "payment_idempotency",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_payment_idempotency_actor_operation_key",
            columnNames = {"actor_user_id", "operation", "idempotency_key_hash"}
        ),
        @UniqueConstraint(name = "uk_payment_idempotency_payment", columnNames = "payment_id"),
        @UniqueConstraint(name = "uk_payment_idempotency_reservation", columnNames = "reservation_id")
    },
    check = {
        @CheckConstraint(
            name = "ck_payment_idempotency_operation",
            constraint = "operation = 'PAYMENT_CREATE'"
        ),
        @CheckConstraint(
            name = "ck_payment_idempotency_status",
            constraint = "status REGEXP '^(PROCESSING|SUCCEEDED|FAILED)$'"
        ),
        @CheckConstraint(
            name = "ck_payment_idempotency_result",
            constraint = """
                (status = 'PROCESSING' AND payment_id IS NULL AND reservation_id IS NULL AND completed_at IS NULL)
                OR (status = 'SUCCEEDED' AND (
                    (payment_id IS NOT NULL AND reservation_id IS NULL)
                    OR (payment_id IS NULL AND reservation_id IS NOT NULL)
                ) AND completed_at IS NOT NULL)
                OR (status = 'FAILED' AND payment_id IS NULL AND reservation_id IS NULL AND completed_at IS NOT NULL)
                """
        )
    }
)
public class PaymentIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_idempotency_id")
    private Long paymentIdempotencyId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 30, updatable = false)
    private PaymentIdempotencyOperation operation;

    @Column(name = "idempotency_key_hash", nullable = false, length = 255, updatable = false)
    private String idempotencyKeyHash;

    @Column(name = "request_hash", nullable = false, length = 255, updatable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentIdempotencyStatus status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "payment_id",
        unique = true,
        foreignKey = @ForeignKey(name = "fk_payment_idempotency_payment")
    )
    private Payment payment;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "reservation_id",
        unique = true,
        foreignKey = @ForeignKey(name = "fk_payment_idempotency_reservation")
    )
    private Reservation reservation;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected PaymentIdempotency() {
    }

    public PaymentIdempotency(
        long actorUserId,
        String idempotencyKeyHash,
        String requestHash
    ) {
        if (actorUserId < 1) {
            throw new IllegalArgumentException("actorUserId must be positive");
        }
        this.actorUserId = actorUserId;
        this.operation = PaymentIdempotencyOperation.PAYMENT_CREATE;
        this.idempotencyKeyHash = requireNotBlank(idempotencyKeyHash, "idempotencyKeyHash");
        this.requestHash = requireNotBlank(requestHash, "requestHash");
        this.status = PaymentIdempotencyStatus.PROCESSING;
    }

    public Long getPaymentIdempotencyId() {
        return paymentIdempotencyId;
    }

    public long getActorUserId() {
        return actorUserId;
    }

    public PaymentIdempotencyOperation getOperation() {
        return operation;
    }

    public String getIdempotencyKeyHash() {
        return idempotencyKeyHash;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public PaymentIdempotencyStatus getStatus() {
        return status;
    }

    public Payment getPayment() {
        return payment;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void succeedWithPayment(Payment payment, Instant completedAt) {
        this.payment = requireNotNull(payment, "payment");
        this.reservation = null;
        complete(completedAt, null);
    }

    public void succeedWithReservation(Reservation reservation, Instant completedAt, Instant expiresAt) {
        this.payment = null;
        this.reservation = requireNotNull(reservation, "reservation");
        complete(completedAt, expiresAt);
    }

    private void complete(Instant completedAt, Instant expiresAt) {
        this.status = PaymentIdempotencyStatus.SUCCEEDED;
        this.completedAt = requireNotNull(completedAt, "completedAt");
        this.expiresAt = expiresAt;
    }

    public void setExpiresAtAfterPaymentFinalization(Instant finalizedAt) {
        if (payment == null || reservation != null || status != PaymentIdempotencyStatus.SUCCEEDED) {
            throw new IllegalStateException("only succeeded payment result can be expired after finalization");
        }
        expiresAt = requireNotNull(finalizedAt, "finalizedAt").plus(24, java.time.temporal.ChronoUnit.HOURS);
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

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
