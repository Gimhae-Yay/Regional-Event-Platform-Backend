package io.regionevent.regioneventbackend.domain.reservation.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByQrReference(String qrReference);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
        SELECT reservation
        FROM Reservation reservation
        WHERE reservation.reservationId = :reservationId
        """)
    Optional<Reservation> findByReservationIdForUpdate(@Param("reservationId") Long reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT reservation
        FROM Reservation reservation
        JOIN FETCH reservation.capacityHold
        WHERE reservation.contentSession.sessionId = :sessionId
            AND reservation.status = io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus.CONFIRMED
        ORDER BY reservation.reservationId ASC
        """)
    List<Reservation> findConfirmedBySessionIdForUpdate(@Param("sessionId") Long sessionId);

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
}
