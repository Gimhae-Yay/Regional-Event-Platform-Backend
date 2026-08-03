package io.regionevent.regioneventbackend.domain.content.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public interface ContentLogRepository extends JpaRepository<ContentLog, Long> {

    @EntityGraph(attributePaths = "actor")
    List<ContentLog> findByContentContentIdOrderByDateAscIdAsc(Long contentId);

    @EntityGraph(attributePaths = "content")
    List<ContentLog> findTop2ByContentContentIdAndContentDeletedAtIsNullAndContentStatusOrderByDateDescIdDesc(
        Long contentId,
        ContentStatus contentStatus
    );

    Optional<ContentLog> findTopByContentContentIdAndStatusOrderByDateDescIdDesc(
        Long contentId,
        ContentLogStatus status
    );

    @Query("""
        SELECT contentLog
        FROM ContentLog contentLog
        WHERE contentLog.content.contentId = :contentId
            AND contentLog.status = io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus.ENDED
        ORDER BY contentLog.date DESC, contentLog.id DESC
        """)
    Optional<ContentLog> findLatestEnded(@Param("contentId") Long contentId);
}
