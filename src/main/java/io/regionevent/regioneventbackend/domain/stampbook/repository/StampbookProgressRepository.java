package io.regionevent.regioneventbackend.domain.stampbook.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgress;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;

public interface StampbookProgressRepository extends JpaRepository<StampbookProgress, Long> {

    Optional<StampbookProgress> findByStampbookStampbookIdAndUserUserId(
        Long stampbookId,
        Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT progress
        FROM StampbookProgress progress
        WHERE progress.stampbook.stampbookId = :stampbookId
          AND progress.user.userId = :userId
        """)
    Optional<StampbookProgress> findByStampbookIdAndUserIdForUpdate(
        @Param("stampbookId") Long stampbookId,
        @Param("userId") Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT progress
        FROM StampbookProgress progress
        WHERE progress.stampbook.stampbookId = :stampbookId
          AND progress.status = :status
        ORDER BY progress.stampbookProgressId ASC
        """)
    List<StampbookProgress> findByStampbookIdAndStatusForUpdate(
        @Param("stampbookId") Long stampbookId,
        @Param("status") StampbookProgressStatus status
    );
}
