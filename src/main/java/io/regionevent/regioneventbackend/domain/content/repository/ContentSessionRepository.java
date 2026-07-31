package io.regionevent.regioneventbackend.domain.content.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public interface ContentSessionRepository extends JpaRepository<ContentSession, Long> {

    Optional<ContentSession> findBySessionIdAndContentStatus(
        Long sessionId,
        ContentStatus contentStatus
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
}
