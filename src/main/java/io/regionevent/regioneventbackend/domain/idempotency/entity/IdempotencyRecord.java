package io.regionevent.regioneventbackend.domain.idempotency.entity;

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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;

@Entity
@Table(
    name = "idempotency_record",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_idempotency_record_actor_operation_key",
        columnNames = {"actor_user_id", "operation", "idempotency_key_hash"}
    ),
    check = {
        @CheckConstraint(
            name = "ck_idempotency_record_operation",
            constraint = "operation REGEXP '^(RESERVATION_CONFIRM|CHECK_IN)$'"
        ),
        @CheckConstraint(
            name = "ck_idempotency_record_status",
            constraint = "status REGEXP '^(PROCESSING|SUCCEEDED|FAILED)$'"
        ),
        @CheckConstraint(
            name = "ck_idempotency_record_processing_result",
            constraint = """
                status <> 'PROCESSING'
                OR (result_reservation_id IS NULL AND result_visit_id IS NULL)
                """
        ),
        @CheckConstraint(
            name = "ck_idempotency_record_failed_result",
            constraint = """
                status <> 'FAILED'
                OR (result_reservation_id IS NULL AND result_visit_id IS NULL)
                """
        ),
        @CheckConstraint(
            name = "ck_idempotency_record_reservation_result",
            constraint = """
                status <> 'SUCCEEDED'
                OR operation <> 'RESERVATION_CONFIRM'
                OR (result_reservation_id IS NOT NULL AND result_visit_id IS NULL)
                """
        ),
        @CheckConstraint(
            name = "ck_idempotency_record_visit_result",
            constraint = """
                status <> 'SUCCEEDED'
                OR operation <> 'CHECK_IN'
                OR (result_reservation_id IS NULL AND result_visit_id IS NOT NULL)
                """
        )
    }
)
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idempotency_record_id")
    private Long idempotencyRecordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "actor_user_id",
        foreignKey = @ForeignKey(name = "fk_idempotency_record_actor")
    )
    private AppUser actor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "operation", nullable = false, length = 30)
    private IdempotencyOperation operation;

    @Column(name = "idempotency_key_hash", length = 255)
    private String idempotencyKeyHash;

    @Column(name = "request_hash", nullable = false, length = 255)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private IdempotencyRecordStatus status;

    @Column(name = "result_code", length = 100)
    private String resultCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "result_reservation_id",
        foreignKey = @ForeignKey(name = "fk_idempotency_record_reservation")
    )
    private Reservation resultReservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "result_visit_id",
        foreignKey = @ForeignKey(name = "fk_idempotency_record_visit")
    )
    private Visit resultVisit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(
        AppUser actor,
        IdempotencyOperation operation,
        String idempotencyKeyHash,
        String requestHash,
        IdempotencyRecordStatus status,
        String resultCode,
        Reservation resultReservation,
        Visit resultVisit,
        Instant createdAt,
        Instant completedAt,
        Instant expiresAt
    ) {
        this.actor = actor;
        this.operation = requireNotNull(operation, "operation");
        this.idempotencyKeyHash = idempotencyKeyHash;
        this.requestHash = requireNonBlank(requestHash, "requestHash");
        this.status = requireNotNull(status, "status");
        this.resultCode = resultCode;
        this.resultReservation = resultReservation;
        this.resultVisit = resultVisit;
        this.createdAt = requireNotNull(createdAt, "createdAt");
        this.completedAt = completedAt;
        this.expiresAt = requireNotNull(expiresAt, "expiresAt");
        validateResultFields();
    }

    public Long getIdempotencyRecordId() {
        return idempotencyRecordId;
    }

    public AppUser getActor() {
        return actor;
    }

    public IdempotencyOperation getOperation() {
        return operation;
    }

    public String getIdempotencyKeyHash() {
        return idempotencyKeyHash;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public IdempotencyRecordStatus getStatus() {
        return status;
    }

    public String getResultCode() {
        return resultCode;
    }

    public Reservation getResultReservation() {
        return resultReservation;
    }

    public Visit getResultVisit() {
        return resultVisit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    private void validateResultFields() {
        if (status == IdempotencyRecordStatus.PROCESSING || status == IdempotencyRecordStatus.FAILED) {
            validateNoResult();
            return;
        }

        if (operation == IdempotencyOperation.RESERVATION_CONFIRM) {
            validateReservationResult();
            return;
        }
        validateVisitResult();
    }

    private void validateNoResult() {
        if (resultReservation != null || resultVisit != null) {
            throw new IllegalArgumentException("processing or failed record must not have a result");
        }
    }

    private void validateReservationResult() {
        if (resultReservation == null || resultVisit != null) {
            throw new IllegalArgumentException("reservation confirmation must have only a reservation result");
        }
    }

    private void validateVisitResult() {
        if (resultReservation != null || resultVisit == null) {
            throw new IllegalArgumentException("check-in must have only a visit result");
        }
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }
}
