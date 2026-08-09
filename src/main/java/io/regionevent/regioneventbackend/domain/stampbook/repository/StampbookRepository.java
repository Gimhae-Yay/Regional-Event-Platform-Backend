package io.regionevent.regioneventbackend.domain.stampbook.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;

public interface StampbookRepository extends JpaRepository<Stampbook, Long> {

    @EntityGraph(attributePaths = {"region", "rewardCouponPolicy"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT stampbook
        FROM Stampbook stampbook
        WHERE stampbook.stampbookId = :stampbookId
        """)
    Optional<Stampbook> findByStampbookIdForUpdate(@Param("stampbookId") Long stampbookId);
}
