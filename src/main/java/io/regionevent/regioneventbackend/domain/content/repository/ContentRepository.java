package io.regionevent.regioneventbackend.domain.content.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public interface ContentRepository extends JpaRepository<Content, Long> {

    @EntityGraph(attributePaths = "region")
    Optional<Content> findByContentId(Long contentId);

    boolean existsByContentIdAndStatusAndDeletedAtIsNull(
        Long contentId,
        ContentStatus status
    );
}
