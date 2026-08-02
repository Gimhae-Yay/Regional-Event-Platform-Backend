package io.regionevent.regioneventbackend.domain.content.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
        SELECT revision.content.contentId
        FROM ContentRevision revision
        WHERE revision.contentRevisionId = :revisionId
        """)
    Optional<Long> findContentIdByContentRevisionId(@Param("revisionId") Long revisionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT revision
        FROM ContentRevision revision
        JOIN FETCH revision.content content
        JOIN FETCH content.region
        WHERE revision.contentRevisionId = :revisionId
          AND content.deletedAt IS NULL
        """)
    Optional<ContentRevision> findReviewTargetByIdForUpdate(@Param("revisionId") Long revisionId);

    @EntityGraph(attributePaths = {"content", "content.operator", "candidateImageObject"})
    List<ContentRevision>
        findByContentRegionRegionIdAndStatusAndContentDeletedAtIsNullOrderBySubmittedAtAscContentRevisionIdAsc(
            Long regionId,
            ContentRevisionStatus status
        );
}
