package io.regionevent.regioneventbackend.domain.reservation.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;

public interface CapacityHoldRepository extends JpaRepository<CapacityHold, Long> {

    @EntityGraph(attributePaths = {"region", "contentSession", "contentSession.content", "user"})
    Optional<CapacityHold> findByHoldId(Long holdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT capacityHold
        FROM CapacityHold capacityHold
        WHERE capacityHold.contentSession.sessionId = :sessionId
            AND capacityHold.status = io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus.ACTIVE
        ORDER BY capacityHold.holdId ASC
        """)
    List<CapacityHold> findActiveBySessionIdForUpdate(@Param("sessionId") Long sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE capacity_hold
        SET status = 'CONSUMED',
            terminal_at = CURRENT_TIMESTAMP
        WHERE hold_id = :holdId
            AND user_id = :userId
            AND status = 'ACTIVE'
            AND expires_at > CURRENT_TIMESTAMP
            AND EXISTS (
                SELECT 1
                FROM content_session
                JOIN content ON content.content_id = content_session.content_id
                WHERE content_session.session_id = capacity_hold.session_id
                    AND content.status = 'PUBLISHED'
                    AND content_session.status = 'SCHEDULED'
                    AND content_session.starts_at > CURRENT_TIMESTAMP
            )
        """, nativeQuery = true)
    int consumeIfConfirmable(
        @Param("holdId") Long holdId,
        @Param("userId") Long userId
    );

    @Query(value = """
        SELECT hold_id
        FROM capacity_hold
        WHERE status = 'ACTIVE'
            AND expires_at <= CURRENT_TIMESTAMP
        ORDER BY hold_id ASC
        """, nativeQuery = true)
    List<Long> findExpiredActiveHoldIds();

    @Query(value = """
        SELECT capacity_hold.hold_id
        FROM capacity_hold
        JOIN content_session ON content_session.session_id = capacity_hold.session_id
            AND content_session.region_id = capacity_hold.region_id
        WHERE capacity_hold.status = 'ACTIVE'
            AND content_session.starts_at <= CURRENT_TIMESTAMP
        ORDER BY capacity_hold.hold_id ASC
        """, nativeQuery = true)
    List<Long> findActiveHoldIdsForStartedSessions();

    @Query("""
        SELECT capacityHold.holdId
        FROM CapacityHold capacityHold
        WHERE capacityHold.contentSession.content.contentId = :contentId
            AND capacityHold.status = io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus.ACTIVE
        ORDER BY capacityHold.holdId ASC
        """)
    List<Long> findActiveHoldIdsByContentId(@Param("contentId") Long contentId);

    @Query("""
        SELECT capacityHold.holdId
        FROM CapacityHold capacityHold
        WHERE capacityHold.user.userId = :userId
            AND capacityHold.status = io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus.ACTIVE
        ORDER BY capacityHold.holdId ASC
        """)
    List<Long> findActiveHoldIdsByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT capacityHold.contentSession.sessionId
        FROM CapacityHold capacityHold
        WHERE capacityHold.holdId = :holdId
        """)
    Optional<Long> findContentSessionIdByHoldId(@Param("holdId") Long holdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT capacityHold
        FROM CapacityHold capacityHold
        WHERE capacityHold.holdId = :holdId
            AND capacityHold.status = io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus.ACTIVE
        """)
    Optional<CapacityHold> findActiveByHoldIdForUpdate(@Param("holdId") Long holdId);

    @Query(value = """
        SELECT hold_id
        FROM capacity_hold
        WHERE hold_id = :holdId
            AND status = 'ACTIVE'
            AND expires_at <= CURRENT_TIMESTAMP
        FOR UPDATE
        """, nativeQuery = true)
    Optional<Long> findExpiredActiveHoldIdForUpdate(@Param("holdId") Long holdId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE capacity_hold
        JOIN content_session ON content_session.session_id = capacity_hold.session_id
            AND content_session.region_id = capacity_hold.region_id
        SET capacity_hold.status = 'EXPIRED',
            capacity_hold.terminal_at = CURRENT_TIMESTAMP,
            capacity_hold.invalidation_reason = NULL,
            capacity_hold.capacity_released_at = CURRENT_TIMESTAMP,
            content_session.remaining_capacity = content_session.remaining_capacity + capacity_hold.quantity,
            content_session.version_no = content_session.version_no + 1,
            content_session.updated_at = CURRENT_TIMESTAMP
        WHERE capacity_hold.hold_id = :holdId
            AND capacity_hold.status = 'ACTIVE'
            AND capacity_hold.expires_at <= CURRENT_TIMESTAMP
        """, nativeQuery = true)
    int expireAndReleaseCapacityIfActive(@Param("holdId") Long holdId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE capacity_hold
        JOIN content_session ON content_session.session_id = capacity_hold.session_id
            AND content_session.region_id = capacity_hold.region_id
        SET capacity_hold.status = CASE
                WHEN content_session.starts_at <= CURRENT_TIMESTAMP THEN 'INVALIDATED'
                ELSE 'EXPIRED'
            END,
            capacity_hold.terminal_at = CURRENT_TIMESTAMP,
            capacity_hold.invalidation_reason = CASE
                WHEN content_session.starts_at <= CURRENT_TIMESTAMP THEN :invalidationReason
                ELSE NULL
            END,
            capacity_hold.capacity_released_at = CURRENT_TIMESTAMP,
            content_session.remaining_capacity = content_session.remaining_capacity + capacity_hold.quantity,
            content_session.version_no = content_session.version_no + 1,
            content_session.updated_at = CURRENT_TIMESTAMP
        WHERE capacity_hold.hold_id = :holdId
            AND capacity_hold.status = 'ACTIVE'
            AND capacity_hold.expires_at <= CURRENT_TIMESTAMP
        """, nativeQuery = true)
    int expireOrInvalidateExpiredHoldAndReleaseCapacityIfActive(
        @Param("holdId") Long holdId,
        @Param("invalidationReason") String invalidationReason
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE capacity_hold
        JOIN content_session ON content_session.session_id = capacity_hold.session_id
            AND content_session.region_id = capacity_hold.region_id
        SET capacity_hold.status = 'INVALIDATED',
            capacity_hold.terminal_at = CURRENT_TIMESTAMP,
            capacity_hold.invalidation_reason = :invalidationReason,
            capacity_hold.capacity_released_at = CURRENT_TIMESTAMP,
            content_session.remaining_capacity = content_session.remaining_capacity + capacity_hold.quantity,
            content_session.version_no = content_session.version_no + 1,
            content_session.updated_at = CURRENT_TIMESTAMP
        WHERE capacity_hold.hold_id = :holdId
            AND capacity_hold.status = 'ACTIVE'
        """, nativeQuery = true)
    int invalidateAndReleaseCapacityIfActive(
        @Param("holdId") Long holdId,
        @Param("invalidationReason") String invalidationReason
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE capacity_hold SET user_id = NULL WHERE user_id = :userId", nativeQuery = true)
    int unlinkUserByUserId(@Param("userId") Long userId);
}
