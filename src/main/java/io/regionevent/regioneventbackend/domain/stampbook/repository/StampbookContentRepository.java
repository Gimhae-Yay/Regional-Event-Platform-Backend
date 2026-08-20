package io.regionevent.regioneventbackend.domain.stampbook.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContent;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContentId;

public interface StampbookContentRepository extends JpaRepository<StampbookContent, StampbookContentId> {

    long countByStampbookStampbookId(Long stampbookId);

    @Query("""
        SELECT stampbookContent.content.contentId
        FROM StampbookContent stampbookContent
        WHERE stampbookContent.stampbook.stampbookId = :stampbookId
        ORDER BY stampbookContent.content.contentId ASC
        """)
    List<Long> findContentIdsByStampbookId(@Param("stampbookId") Long stampbookId);

    @Query("""
        SELECT stampbookContent
        FROM StampbookContent stampbookContent
        JOIN FETCH stampbookContent.content content
        JOIN FETCH content.region contentRegion
        JOIN FETCH content.operator contentOperator
        WHERE stampbookContent.stampbook.stampbookId = :stampbookId
        ORDER BY content.contentId ASC
        """)
    List<StampbookContent> findDetailByStampbookId(@Param("stampbookId") Long stampbookId);

    @Modifying(flushAutomatically = true)
    @Query("""
        DELETE FROM StampbookContent stampbookContent
        WHERE stampbookContent.stampbook.stampbookId = :stampbookId
        """)
    int deleteByStampbookId(@Param("stampbookId") Long stampbookId);
}
