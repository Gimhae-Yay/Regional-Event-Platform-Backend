package io.regionevent.regioneventbackend.domain.content.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public interface ContentSessionRepository extends JpaRepository<ContentSession, Long> {

    List<ContentSession> findByContentContentIdOrderByStartsAtAscSessionIdAsc(Long contentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT contentSession
        FROM ContentSession contentSession
        WHERE contentSession.content.contentId = :contentId
        ORDER BY contentSession.startsAt ASC, contentSession.sessionId ASC
        """)
    List<ContentSession> findApprovalTargetsForUpdate(@Param("contentId") Long contentId);

    Optional<ContentSession> findBySessionIdAndContentStatus(
        Long sessionId,
        ContentStatus contentStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"content", "content.operator", "region"})
    @Query("""
        SELECT contentSession
        FROM ContentSession contentSession
        WHERE contentSession.sessionId = :sessionId
            AND contentSession.content.deletedAt IS NULL
        """)
    Optional<ContentSession> findCancelTargetForUpdate(@Param("sessionId") Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT contentSession
        FROM ContentSession contentSession
        WHERE contentSession.sessionId = :sessionId
            AND contentSession.content.status = :contentStatus
            AND contentSession.content.deletedAt IS NULL
            AND contentSession.status = :sessionStatus
            AND contentSession.startsAt > CURRENT_TIMESTAMP
        """)
    Optional<ContentSession> findConfirmableReservationTargetForUpdate(
        @Param("sessionId") Long sessionId,
        @Param("contentStatus") ContentStatus contentStatus,
        @Param("sessionStatus") ContentSessionStatus sessionStatus
    );

    List<ContentSession> findByContentContentIdAndStatusOrderByStartsAtAsc(
        Long contentId,
        ContentSessionStatus status
    );

    @Query("""
        SELECT contentSession.sessionId AS sessionId,
            contentSession.content.contentId AS contentId,
            contentSession.startsAt AS startsAt,
            contentSession.endsAt AS endsAt,
            contentSession.remainingCapacity AS remainingCapacity,
            CASE WHEN contentSession.startsAt > CURRENT_TIMESTAMP THEN true ELSE false END AS startsBeforeNow
        FROM ContentSession contentSession
        WHERE contentSession.sessionId = :sessionId
            AND contentSession.content.status = :contentStatus
            AND contentSession.content.deletedAt IS NULL
            AND contentSession.status = :sessionStatus
        """)
    Optional<PublicSessionReservationInfoProjection> findPublicScheduledReservationInfo(
        @Param("sessionId") Long sessionId,
        @Param("contentStatus") ContentStatus contentStatus,
        @Param("sessionStatus") ContentSessionStatus sessionStatus
    );

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE ContentSession contentSession
        SET contentSession.remainingCapacity = contentSession.remainingCapacity - :quantity
        WHERE contentSession.sessionId = :sessionId
            AND contentSession.content.status = :contentStatus
            AND contentSession.status = :sessionStatus
            AND contentSession.startsAt > CURRENT_TIMESTAMP
            AND contentSession.remainingCapacity >= :quantity
        """)
    int decreaseRemainingCapacityIfReservable(
        @Param("sessionId") Long sessionId,
        @Param("quantity") int quantity,
        @Param("contentStatus") ContentStatus contentStatus,
        @Param("sessionStatus") ContentSessionStatus sessionStatus
    );

    @Query("""
        SELECT contentSession.sessionId
        FROM ContentSession contentSession
        WHERE contentSession.status = :scheduledStatus
            AND contentSession.endsAt <= CURRENT_TIMESTAMP
            AND contentSession.checkinCloseAt <= CURRENT_TIMESTAMP
        ORDER BY contentSession.sessionId ASC
        """)
    List<Long> findNoShowProcessingTargetSessionIds(
        @Param("scheduledStatus") ContentSessionStatus scheduledStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT contentSession
        FROM ContentSession contentSession
        WHERE contentSession.sessionId = :sessionId
            AND contentSession.status = :scheduledStatus
            AND contentSession.endsAt <= CURRENT_TIMESTAMP
            AND contentSession.checkinCloseAt <= CURRENT_TIMESTAMP
        """)
    Optional<ContentSession> findNoShowProcessingTargetForUpdate(
        @Param("sessionId") Long sessionId,
        @Param("scheduledStatus") ContentSessionStatus scheduledStatus
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE content_session
        SET status = 'COMPLETED',
            completed_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP,
            version_no = version_no + 1
        WHERE session_id = :sessionId
            AND status = 'SCHEDULED'
            AND ends_at <= CURRENT_TIMESTAMP
            AND checkin_close_at <= CURRENT_TIMESTAMP
            AND NOT EXISTS (
                SELECT 1
                FROM reservation
                WHERE reservation.session_id = content_session.session_id
                    AND reservation.status = 'CONFIRMED'
            )
        """, nativeQuery = true)
    int completeIfNoConfirmedReservation(@Param("sessionId") Long sessionId);

    @Query("""
        SELECT contentSession
        FROM ContentSession contentSession
        JOIN FETCH contentSession.region
        JOIN FETCH contentSession.content
        WHERE contentSession.sessionId = :sessionId
            AND contentSession.status = :completedStatus
        """)
    Optional<ContentSession> findCompletedSessionForNoShowAudit(
        @Param("sessionId") Long sessionId,
        @Param("completedStatus") ContentSessionStatus completedStatus
    );
}
