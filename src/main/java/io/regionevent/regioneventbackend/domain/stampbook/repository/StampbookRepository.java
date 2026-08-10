package io.regionevent.regioneventbackend.domain.stampbook.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public interface StampbookRepository extends JpaRepository<Stampbook, Long> {

    @EntityGraph(attributePaths = {"region", "rewardCouponPolicy"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT stampbook
        FROM Stampbook stampbook
        WHERE stampbook.stampbookId = :stampbookId
        """)
    Optional<Stampbook> findByStampbookIdForUpdate(@Param("stampbookId") Long stampbookId);

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.stampbook.repository.MyStampbookListProjection(
            stampbook.stampbookId,
            stampbook.region.regionId,
            stampbook.status,
            stampbook.publishedAt,
            progress.status,
            progress.completedAt,
            COUNT(DISTINCT stampEarn.stampEarnId),
            COUNT(DISTINCT stampbookContent.content.contentId),
            MAX(stampEarn.earnedAt)
        )
        FROM Stampbook stampbook
        LEFT JOIN StampbookProgress progress
            ON progress.stampbook = stampbook
            AND progress.user.userId = :userId
        LEFT JOIN StampEarn stampEarn ON stampEarn.stampbookProgress = progress
        LEFT JOIN StampbookContent stampbookContent ON stampbookContent.stampbook = stampbook
        WHERE stampbook.status = :publishedStatus
           OR stampbook.status = :endedStatus
            AND progress.stampbookProgressId IS NOT NULL
        GROUP BY stampbook.stampbookId,
            stampbook.region.regionId,
            stampbook.status,
            stampbook.publishedAt,
            progress.status,
            progress.completedAt
        ORDER BY stampbook.publishedAt DESC, stampbook.stampbookId DESC
        """)
    List<MyStampbookListProjection> findMyStampbookListProjections(
        @Param("userId") Long userId,
        @Param("publishedStatus") StampbookStatus publishedStatus,
        @Param("endedStatus") StampbookStatus endedStatus
    );
}
