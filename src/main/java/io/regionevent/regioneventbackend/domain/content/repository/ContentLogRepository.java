package io.regionevent.regioneventbackend.domain.content.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
