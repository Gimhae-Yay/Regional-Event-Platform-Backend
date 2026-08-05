package io.regionevent.regioneventbackend.domain.content.repository;

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
}
