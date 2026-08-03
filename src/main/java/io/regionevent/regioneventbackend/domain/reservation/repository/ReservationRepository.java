package io.regionevent.regioneventbackend.domain.reservation.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;

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

    @Query("""
        SELECT reservation.reservationId
        FROM Reservation reservation
        WHERE reservation.contentSession.sessionId = :sessionId
            AND reservation.status = :confirmedStatus
        ORDER BY reservation.reservationId ASC
        """)
    List<Long> findConfirmedReservationIdsBySessionId(
        @Param("sessionId") Long sessionId,
        @Param("confirmedStatus") ReservationStatus confirmedStatus
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE reservation
        JOIN content_session ON content_session.session_id = reservation.session_id
        SET reservation.status = 'EXPIRED',
            reservation.expired_at = CURRENT_TIMESTAMP,
            reservation.updated_at = CURRENT_TIMESTAMP
        WHERE reservation.reservation_id = :reservationId
            AND reservation.status = 'CONFIRMED'
            AND content_session.status = 'SCHEDULED'
            AND content_session.ends_at <= CURRENT_TIMESTAMP
            AND content_session.checkin_close_at <= CURRENT_TIMESTAMP
        """, nativeQuery = true)
    int expireIfNoShowEligible(@Param("reservationId") Long reservationId);

    @EntityGraph(attributePaths = "region")
    Optional<Reservation> findByReservationIdAndStatus(
        Long reservationId,
        ReservationStatus status
    );
}
