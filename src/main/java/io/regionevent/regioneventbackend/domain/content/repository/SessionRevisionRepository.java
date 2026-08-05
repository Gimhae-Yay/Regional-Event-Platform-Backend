package io.regionevent.regioneventbackend.domain.content.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;

public interface SessionRevisionRepository extends JpaRepository<SessionRevision, Long> {

    @Query("""
        SELECT sessionRevision.content.contentId
        FROM SessionRevision sessionRevision
        WHERE sessionRevision.sessionRevisionId = :revisionId
        """)
    Optional<Long> findContentIdBySessionRevisionId(@Param("revisionId") Long revisionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT sessionRevision
        FROM SessionRevision sessionRevision
        JOIN FETCH sessionRevision.content
        JOIN FETCH sessionRevision.region
        JOIN FETCH sessionRevision.targetSession
        WHERE sessionRevision.sessionRevisionId = :revisionId
        """)
    Optional<SessionRevision> findApprovalTargetForUpdate(@Param("revisionId") Long revisionId);

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
