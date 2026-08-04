package io.regionevent.regioneventbackend.domain.reservation.repository;

import java.math.BigDecimal;
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

    @EntityGraph(attributePaths = {
        "region",
        "contentSession",
        "contentSession.region",
        "contentSession.content",
        "contentSession.content.region",
        "contentSession.content.operator",
        "user"
    })
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT reservation
        FROM Reservation reservation
        WHERE reservation.qrReference = :qrReference
        """)
    Optional<Reservation> findByQrReferenceForCheckIn(@Param("qrReference") String qrReference);

    @EntityGraph(attributePaths = {
        "region",
        "contentSession",
        "contentSession.region",
        "contentSession.content",
        "contentSession.content.region",
        "contentSession.content.operator",
        "user"
    })
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reservation> findByReservationNo(String reservationNo);

    @EntityGraph(attributePaths = {
        "region",
        "contentSession",
        "contentSession.region",
        "contentSession.content",
        "contentSession.content.region",
        "contentSession.content.operator"
    })
    @Query("""
        SELECT reservation
        FROM Reservation reservation
        WHERE reservation.reservationNo = :reservationNo
        """)
    Optional<Reservation> findByReservationNoForAuthorizedLookup(
        @Param("reservationNo") String reservationNo
    );

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.reservation.repository.ReservationReadProjection(
            reservation.reservationId,
            reservation.reservationNo,
            reservation.status,
            reservation.confirmedAt,
            reservation.cancelledAt,
            reservation.cancellationReason,
            reservation.expiredAt,
            reservation.region.regionId,
            contentSession.sessionId,
            contentSession.status,
            contentSession.startsAt,
            contentSession.endsAt,
            contentSession.checkinOpenAt,
            contentSession.checkinCloseAt,
            contentSession.region.regionId,
            content.contentId,
            content.title,
            content.region.regionId,
            participant.userId,
            participant.name,
            participant.phone,
            visit.visitId,
            visit.reservation.reservationId,
            visit.region.regionId,
            visit.contentSession.sessionId,
            visit.content.contentId,
            visitParticipant.userId,
            visit.checkedAt
        )
        FROM Reservation reservation
        JOIN reservation.contentSession contentSession
        JOIN contentSession.content content
        LEFT JOIN reservation.user participant
        LEFT JOIN Visit visit ON visit.reservation = reservation
        LEFT JOIN visit.user visitParticipant
        WHERE reservation.reservationNo = :reservationNo
        ORDER BY visit.visitId ASC
        """)
    List<ReservationReadProjection> findReadProjectionsByReservationNo(
        @Param("reservationNo") String reservationNo
    );

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.reservation.repository.ReservationReadProjection(
            reservation.reservationId,
            reservation.reservationNo,
            reservation.status,
            reservation.confirmedAt,
            reservation.cancelledAt,
            reservation.cancellationReason,
            reservation.expiredAt,
            reservation.region.regionId,
            contentSession.sessionId,
            contentSession.status,
            contentSession.startsAt,
            contentSession.endsAt,
            contentSession.checkinOpenAt,
            contentSession.checkinCloseAt,
            contentSession.region.regionId,
            content.contentId,
            content.title,
            content.region.regionId,
            participant.userId,
            participant.name,
            participant.phone,
            visit.visitId,
            visit.reservation.reservationId,
            visit.region.regionId,
            visit.contentSession.sessionId,
            visit.content.contentId,
            visitParticipant.userId,
            visit.checkedAt
        )
        FROM Reservation reservation
        JOIN reservation.contentSession contentSession
        JOIN contentSession.content content
        LEFT JOIN reservation.user participant
        LEFT JOIN Visit visit ON visit.reservation = reservation
        LEFT JOIN visit.user visitParticipant
        WHERE reservation.reservationId = :reservationId
        ORDER BY visit.visitId ASC
        """)
    List<ReservationReadProjection> findReadProjectionsByReservationId(
        @Param("reservationId") Long reservationId
    );

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.reservation.repository.ReservationReadProjection(
            reservation.reservationId,
            reservation.reservationNo,
            reservation.status,
            reservation.confirmedAt,
            reservation.cancelledAt,
            reservation.cancellationReason,
            reservation.expiredAt,
            reservation.region.regionId,
            contentSession.sessionId,
            contentSession.status,
            contentSession.startsAt,
            contentSession.endsAt,
            contentSession.checkinOpenAt,
            contentSession.checkinCloseAt,
            contentSession.region.regionId,
            content.contentId,
            content.title,
            content.region.regionId,
            participant.userId,
            participant.name,
            participant.phone,
            visit.visitId,
            visit.reservation.reservationId,
            visit.region.regionId,
            visit.contentSession.sessionId,
            visit.content.contentId,
            visitParticipant.userId,
            visit.checkedAt
        )
        FROM Reservation reservation
        JOIN reservation.contentSession contentSession
        JOIN contentSession.content content
        JOIN reservation.user participant
        LEFT JOIN Visit visit ON visit.reservation = reservation
        LEFT JOIN visit.user visitParticipant
        WHERE participant.userId = :userId
        ORDER BY reservation.confirmedAt DESC, reservation.reservationId DESC, visit.visitId ASC
        """)
    List<ReservationReadProjection> findReadProjectionsByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.reservation.repository.SessionReservationReadProjection(
            reservation.reservationId,
            reservation.reservationNo,
            reservation.status,
            reservation.confirmedAt,
            reservation.region.regionId,
            contentSession.sessionId,
            contentSession.status,
            contentSession.startsAt,
            contentSession.endsAt,
            contentSession.checkinOpenAt,
            contentSession.checkinCloseAt,
            contentSession.region.regionId,
            content.contentId,
            content.title,
            content.region.regionId,
            participant.userId,
            participant.name,
            participant.phone,
            visit.visitId,
            visit.reservation.reservationId,
            visit.region.regionId,
            visit.contentSession.sessionId,
            visit.content.contentId,
            visitParticipant.userId,
            visit.checkedAt,
            capacityHold.holdId,
            capacityHold.status,
            capacityHold.quantity,
            holdSession.sessionId,
            holdRegion.regionId,
            holdReservation.reservationId
        )
        FROM Reservation reservation
        JOIN reservation.contentSession contentSession
        JOIN contentSession.content content
        LEFT JOIN reservation.user participant
        LEFT JOIN Visit visit ON visit.reservation = reservation
        LEFT JOIN visit.user visitParticipant
        LEFT JOIN reservation.capacityHold capacityHold
        LEFT JOIN capacityHold.contentSession holdSession
        LEFT JOIN capacityHold.region holdRegion
        LEFT JOIN capacityHold.reservation holdReservation
        WHERE contentSession.sessionId = :sessionId
        ORDER BY reservation.confirmedAt ASC, reservation.reservationId ASC, visit.visitId ASC
        """)
    List<SessionReservationReadProjection> findSessionReservationReadProjections(
        @Param("sessionId") Long sessionId
    );

    @EntityGraph(attributePaths = {
        "region",
        "contentSession",
        "contentSession.region",
        "contentSession.content",
        "contentSession.content.region",
        "contentSession.content.operator",
        "user"
    })
    @Query("""
        SELECT reservation
        FROM Reservation reservation
        WHERE reservation.reservationId = :reservationId
        """)
    Optional<Reservation> findWithCheckInDetailsByReservationId(@Param("reservationId") Long reservationId);

    @Query("""
        SELECT reservation
        FROM Reservation reservation
        LEFT JOIN FETCH reservation.user
        JOIN FETCH reservation.contentSession
        WHERE reservation.reservationId = :reservationId
        """)
    Optional<Reservation> findByReservationIdForQrIssue(@Param("reservationId") Long reservationId);

    @Query(value = "SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))", nativeQuery = true)
    BigDecimal findCurrentEpochSeconds();
    @EntityGraph(attributePaths = {"region", "capacityHold", "contentSession", "user"})
    Optional<Reservation> findWithDetailsByReservationId(Long reservationId);

    @Query("""
        SELECT reservation.user.userId AS userId,
            reservation.contentSession.sessionId AS sessionId
        FROM Reservation reservation
        WHERE reservation.reservationId = :reservationId
        """)
    Optional<ReservationCancellationLockTargetProjection> findCancellationLockTargetByReservationId(
        @Param("reservationId") Long reservationId
    );

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

    @Query("""
        SELECT CASE WHEN COUNT(contentSession) > 0 THEN true ELSE false END
        FROM ContentSession contentSession
        WHERE contentSession.sessionId = :sessionId
            AND contentSession.startsAt > CURRENT_TIMESTAMP
        """)
    boolean isSessionBeforeStartByDatabaseTime(@Param("sessionId") Long sessionId);

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE reservation
        SET status = 'CHECKED_IN',
            updated_at = CURRENT_TIMESTAMP
        WHERE reservation_id = :reservationId
            AND status = 'CONFIRMED'
        """, nativeQuery = true)
    int checkInIfConfirmed(@Param("reservationId") Long reservationId);

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
