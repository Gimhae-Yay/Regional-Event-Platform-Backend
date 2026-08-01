package io.regionevent.regioneventbackend.domain.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;

public interface ContentRevisionRepository extends JpaRepository<ContentRevision, Long> {

    boolean existsByContentContentIdAndStatus(Long contentId, ContentRevisionStatus status);

    @Query("""
        SELECT COALESCE(MAX(contentRevision.revisionNo), 0)
        FROM ContentRevision contentRevision
        WHERE contentRevision.content.contentId = :contentId
        """)
    int findMaxRevisionNoByContentId(@Param("contentId") Long contentId);
}
