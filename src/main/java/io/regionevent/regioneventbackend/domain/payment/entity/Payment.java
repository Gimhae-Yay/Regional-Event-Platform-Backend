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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;

@Entity
@Table(
    name = "payment",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_order", columnNames = "order_id"),
        @UniqueConstraint(name = "uk_payment_portone_payment", columnNames = "portone_payment_id"),
        @UniqueConstraint(name = "uk_payment_reservation", columnNames = "reservation_id")
    },
    check = {
        @CheckConstraint(
            name = "ck_payment_status",
            constraint = "status REGEXP '^(PENDING|APPROVED|DECLINED|CANCELLED|EXPIRED|DISCREPANT)$'"
        ),
        @CheckConstraint(
            name = "ck_payment_finalized_at",
            constraint = """
                (status = 'PENDING' AND finalized_at IS NULL)
                OR (status <> 'PENDING' AND finalized_at IS NOT NULL)
                """
        )
    }
)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "hold_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_payment_hold")
    )
    private CapacityHold capacityHold;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "reservation_price_snapshot_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_payment_reservation_price_snapshot")
    )
    private ReservationPriceSnapshot reservationPriceSnapshot;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "reservation_id",
        unique = true,
        foreignKey = @ForeignKey(name = "fk_payment_reservation")
    )
    private Reservation reservation;

    @Column(name = "order_id", nullable = false, length = 255, updatable = false)
    private String orderId;

    @Column(name = "portone_payment_id", length = 255)
    private String portonePaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(
        CapacityHold capacityHold,
        ReservationPriceSnapshot reservationPriceSnapshot,
        String orderId
    ) {
        this(capacityHold, reservationPriceSnapshot, orderId, Instant.now());
    }

    public Payment(
        CapacityHold capacityHold,
        ReservationPriceSnapshot reservationPriceSnapshot,
        String orderId,
        Instant createdAt
    ) {
        this.capacityHold = requireNotNull(capacityHold, "capacityHold");
        this.reservationPriceSnapshot = requireNotNull(
            reservationPriceSnapshot,
            "reservationPriceSnapshot"
        );
        validateSnapshotHold(capacityHold, reservationPriceSnapshot);
        this.orderId = requireNotBlank(orderId, "orderId");
        this.status = PaymentStatus.PENDING;
        this.createdAt = requireNotNull(createdAt, "createdAt");
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public CapacityHold getCapacityHold() {
        return capacityHold;
    }

    public ReservationPriceSnapshot getReservationPriceSnapshot() {
        return reservationPriceSnapshot;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getPortonePaymentId() {
        return portonePaymentId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void expire(Instant expiredAt) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("only pending payment can be expired");
        }
        status = PaymentStatus.EXPIRED;
        finalizedAt = requireNotNull(expiredAt, "expiredAt");
    }

    public void approve(Reservation reservation, String portonePaymentId, Instant approvedAt) {
        requirePendingOrExpired();
        this.reservation = requireNotNull(reservation, "reservation");
        this.portonePaymentId = requireNotBlank(portonePaymentId, "portonePaymentId");
        status = PaymentStatus.APPROVED;
        finalizedAt = requireNotNull(approvedAt, "approvedAt");
    }

    public void decline(Instant declinedAt) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("only pending payment can be declined");
        }
        status = PaymentStatus.DECLINED;
        finalizedAt = requireNotNull(declinedAt, "declinedAt");
    }

    public void markDiscrepant(String portonePaymentId, Instant detectedAt) {
        requirePendingOrExpired();
        this.portonePaymentId = requireNotBlank(portonePaymentId, "portonePaymentId");
        status = PaymentStatus.DISCREPANT;
        finalizedAt = requireNotNull(detectedAt, "detectedAt");
    }

    private void requirePendingOrExpired() {
        if (status != PaymentStatus.PENDING && status != PaymentStatus.EXPIRED) {
            throw new IllegalStateException("only pending or expired payment can be finalized");
        }
    }

    private static void validateSnapshotHold(
        CapacityHold capacityHold,
        ReservationPriceSnapshot reservationPriceSnapshot
    ) {
        if (capacityHold != reservationPriceSnapshot.getCapacityHold()) {
            Long holdId = capacityHold.getHoldId();
            Long snapshotHoldId = reservationPriceSnapshot.getCapacityHold().getHoldId();
            if (holdId == null || !holdId.equals(snapshotHoldId)) {
                throw new IllegalArgumentException("reservationPriceSnapshot must belong to capacityHold");
            }
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
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
