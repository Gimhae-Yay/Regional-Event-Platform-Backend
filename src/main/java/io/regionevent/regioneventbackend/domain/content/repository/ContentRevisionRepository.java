package io.regionevent.regioneventbackend.domain.content.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;

public interface ContentRevisionRepository extends JpaRepository<ContentRevision, Long> {

    @EntityGraph(attributePaths = {
        "content",
        "content.region",
        "content.operator",
        "candidateImageObject",
        "candidateImageObject.region"
    })
    Optional<ContentRevision> findByContentRevisionIdAndStatusAndContentDeletedAtIsNull(
        Long contentRevisionId,
        ContentRevisionStatus status
    );

    @EntityGraph(attributePaths = {"content", "content.operator", "candidateImageObject"})
    List<ContentRevision>
        findByContentRegionRegionIdAndStatusAndContentDeletedAtIsNullOrderBySubmittedAtAscContentRevisionIdAsc(
            Long regionId,
            ContentRevisionStatus status
        );
}
