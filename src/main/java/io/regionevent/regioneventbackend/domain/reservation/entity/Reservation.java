package io.regionevent.regioneventbackend.domain.reservation.entity;

import java.time.Instant;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Entity
@Table(
    name = "reservation",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_reservation_reservation_no",
            columnNames = "reservation_no"
        ),
        @UniqueConstraint(
            name = "uk_reservation_qr_reference",
            columnNames = "qr_reference"
        ),
        @UniqueConstraint(
            name = "uk_reservation_hold",
            columnNames = "hold_id"
        ),
        @UniqueConstraint(
            name = "uk_reservation_reservation_session_region",
            columnNames = {"reservation_id", "session_id", "region_id"}
        )
    },
    check = {
        @CheckConstraint(
            name = "ck_reservation_status",
            constraint = "status REGEXP '^(CONFIRMED|CHECKED_IN|CANCELLED|EXPIRED)$'"
        ),
        @CheckConstraint(
            name = "ck_reservation_cancelled",
            constraint = """
                (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL)
                OR (status <> 'CANCELLED' AND cancelled_at IS NULL AND cancellation_reason IS NULL)
                """
        ),
        @CheckConstraint(
            name = "ck_reservation_expired",
            constraint = """
                (status = 'EXPIRED' AND expired_at IS NOT NULL AND capacity_released_at IS NULL)
                OR (status <> 'EXPIRED' AND expired_at IS NULL)
                """
        )
    }
)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "reservation_no", nullable = false, length = 255)
    private String reservationNo;

    @Column(name = "qr_reference", nullable = false, length = 255)
    private String qrReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "region_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Region region;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "hold_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private CapacityHold capacityHold;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "session_id",
        nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private ContentSession contentSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_id",
        foreignKey = @ForeignKey(name = "fk_reservation_user")
    )
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private ReservationStatus status;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "capacity_released_at")
    private Instant capacityReleasedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reservation() {
    }

    public Reservation(
        String reservationNo,
        String qrReference,
        Region region,
        CapacityHold capacityHold,
        ContentSession contentSession,
        AppUser user,
        ReservationStatus status,
        Instant confirmedAt,
        Instant cancelledAt,
        String cancellationReason,
        Instant expiredAt,
        Instant capacityReleasedAt
    ) {
        this.reservationNo = requireNonBlank(reservationNo, "reservationNo");
        this.qrReference = requireNonBlank(qrReference, "qrReference");
        this.region = requireNotNull(region, "region");
        this.capacityHold = requireNotNull(capacityHold, "capacityHold");
        this.contentSession = requireNotNull(contentSession, "contentSession");
        validateHoldSessionRegion(capacityHold, contentSession, region);
        this.user = user;
        this.status = requireNotNull(status, "status");
        this.confirmedAt = requireNotNull(confirmedAt, "confirmedAt");
        this.cancelledAt = cancelledAt;
        this.cancellationReason = cancellationReason;
        this.expiredAt = expiredAt;
        this.capacityReleasedAt = capacityReleasedAt;
        validateStatusFields();
        capacityHold.assignReservation(this);
    }

    @PrePersist
    @PreUpdate
    protected void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public Long getReservationId() {
        return reservationId;
    }

    public String getReservationNo() {
        return reservationNo;
    }

    public String getQrReference() {
        return qrReference;
    }

    public Region getRegion() {
        return region;
    }

    public CapacityHold getCapacityHold() {
        return capacityHold;
    }

    public ContentSession getContentSession() {
        return contentSession;
    }

    public AppUser getUser() {
        return user;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }

    public Instant getCapacityReleasedAt() {
        return capacityReleasedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
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

    private static void validateHoldSessionRegion(
        CapacityHold capacityHold,
        ContentSession contentSession,
        Region region
    ) {
        validateSameEntity(capacityHold.getContentSession(), contentSession, "contentSession");
        validateSameEntity(capacityHold.getRegion(), region, "region");
    }

    private static void validateSameEntity(
        ContentSession expected,
        ContentSession actual,
        String fieldName
    ) {
        if (expected == actual) {
            return;
        }
        Long expectedId = expected.getSessionId();
        Long actualId = actual.getSessionId();
        if (expectedId == null || !expectedId.equals(actualId)) {
            throw new IllegalArgumentException(fieldName + " must match capacityHold");
        }
    }

    private static void validateSameEntity(
        Region expected,
        Region actual,
        String fieldName
    ) {
        if (expected == actual) {
            return;
        }
        Long expectedId = expected.getRegionId();
        Long actualId = actual.getRegionId();
        if (expectedId == null || !expectedId.equals(actualId)) {
            throw new IllegalArgumentException(fieldName + " must match capacityHold");
        }
    }

    private void validateStatusFields() {
        boolean isCancelled = status == ReservationStatus.CANCELLED;
        boolean isExpired = status == ReservationStatus.EXPIRED;

        if ((isCancelled && (cancelledAt == null || cancellationReason == null || cancellationReason.isBlank()))
            || (!isCancelled && (cancelledAt != null || cancellationReason != null))) {
            throw new IllegalArgumentException("cancellation fields do not match reservation status");
        }
        if ((isExpired && (expiredAt == null || capacityReleasedAt != null))
            || (!isExpired && expiredAt != null)) {
            throw new IllegalArgumentException("expiration fields do not match reservation status");
        }
    }
}
