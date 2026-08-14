package io.regionevent.regioneventbackend.domain.stampbook.repository;

import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampEarn;

public interface StampEarnRepository extends JpaRepository<StampEarn, Long> {

    long countByStampbookProgressStampbookProgressId(Long stampbookProgressId);

    boolean existsByStampbookProgressStampbookProgressIdAndVisitVisitId(
        Long stampbookProgressId,
        Long visitId
    );

    boolean existsByStampbookProgressStampbookProgressIdAndContentContentId(
        Long stampbookProgressId,
        Long contentId
    );

    @EntityGraph(attributePaths = {"visit", "content"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT stampEarn
        FROM StampEarn stampEarn
        WHERE stampEarn.stampbookProgress.stampbookProgressId = :stampbookProgressId
        ORDER BY stampEarn.stampEarnId ASC
        """)
    List<StampEarn> findAllByStampbookProgressIdForUpdate(
        @Param("stampbookProgressId") Long stampbookProgressId
    );

    @EntityGraph(attributePaths = {"visit", "content"})
    List<StampEarn> findByStampbookProgressStampbookProgressIdOrderByEarnedAtDescStampEarnIdDesc(
        Long stampbookProgressId
    );
}
