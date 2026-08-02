package io.regionevent.regioneventbackend.domain.reservation.repository;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByQrReference(String qrReference);

    @EntityGraph(attributePaths = {"region", "capacityHold", "contentSession", "user"})
    Optional<Reservation> findWithDetailsByReservationId(Long reservationId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
        SELECT reservation
        FROM Reservation reservation
        WHERE reservation.reservationId = :reservationId
        """)
    Optional<Reservation> findByReservationIdForUpdate(@Param("reservationId") Long reservationId);

    @Modifying
    @Query(value = """
        INSERT INTO reservation (
            reservation_no,
            qr_reference,
            region_id,
            hold_id,
            session_id,
            user_id,
            status,
            confirmed_at,
            updated_at
        ) VALUES (
            :reservationNo,
            :qrReference,
            :regionId,
            :holdId,
            :sessionId,
            :userId,
            'CONFIRMED',
            :confirmedAt,
            :confirmedAt
        )
        """, nativeQuery = true)
    int insertConfirmed(
        @Param("reservationNo") String reservationNo,
        @Param("qrReference") String qrReference,
        @Param("regionId") Long regionId,
        @Param("holdId") Long holdId,
        @Param("sessionId") Long sessionId,
        @Param("userId") Long userId,
        @Param("confirmedAt") Instant confirmedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE reservation
        SET status = 'CANCELLED',
            cancelled_at = CURRENT_TIMESTAMP,
            cancellation_reason = 'USER_REQUEST',
            capacity_released_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE reservation_id = :reservationId
            AND user_id = :userId
            AND status = 'CONFIRMED'
            AND EXISTS (
                SELECT 1
                FROM content_session
                WHERE content_session.session_id = reservation.session_id
                    AND content_session.starts_at > CURRENT_TIMESTAMP
            )
        """, nativeQuery = true)
    int cancelIfCancellable(
        @Param("reservationId") Long reservationId,
        @Param("userId") Long userId
    );
}
