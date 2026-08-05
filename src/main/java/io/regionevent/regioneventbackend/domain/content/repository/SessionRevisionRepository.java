package io.regionevent.regioneventbackend.domain.content.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;

public interface SessionRevisionRepository extends JpaRepository<SessionRevision, Long> {

    @EntityGraph(attributePaths = {
        "content",
        "content.region",
        "region",
        "targetSession",
        "targetSession.content",
        "targetSession.region",
        "requestedBy"
    })
    @Query("""
        SELECT sessionRevision
        FROM SessionRevision sessionRevision
        WHERE sessionRevision.sessionRevisionId = :revisionId
            AND sessionRevision.status = :status
            AND sessionRevision.content.deletedAt IS NULL
        """)
    Optional<SessionRevision> findPendingReviewDetailById(
        @Param("revisionId") Long revisionId,
        @Param("status") SessionRevisionStatus status
    );

    @Query("""
        SELECT sessionRevision
        FROM SessionRevision sessionRevision
        JOIN FETCH sessionRevision.content
        JOIN FETCH sessionRevision.region
        JOIN FETCH sessionRevision.targetSession
        JOIN FETCH sessionRevision.requestedBy
        WHERE sessionRevision.region.regionId = :regionId
            AND sessionRevision.status = :status
            AND sessionRevision.content.deletedAt IS NULL
        ORDER BY sessionRevision.submittedAt ASC, sessionRevision.sessionRevisionId ASC
        """)
    List<SessionRevision> findByRegionIdAndStatusForReview(
        @Param("regionId") Long regionId,
        @Param("status") SessionRevisionStatus status
    );
}
