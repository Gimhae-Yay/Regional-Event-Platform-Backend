package io.regionevent.regioneventbackend.domain.stampbook.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampEarn;

public interface StampEarnRepository extends JpaRepository<StampEarn, Long> {

    long countByStampbookProgressStampbookProgressId(Long stampbookProgressId);

    boolean existsByStampbookProgressStampbookProgressIdAndContentContentId(
        Long stampbookProgressId,
        Long contentId
    );

    @EntityGraph(attributePaths = {"visit", "content"})
    List<StampEarn> findByStampbookProgressStampbookProgressIdOrderByEarnedAtDescStampEarnIdDesc(
        Long stampbookProgressId
    );
}
