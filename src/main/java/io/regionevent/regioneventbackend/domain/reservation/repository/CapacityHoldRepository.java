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
}
