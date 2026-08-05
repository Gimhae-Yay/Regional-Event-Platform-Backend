package io.regionevent.regioneventbackend.domain.content.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT sessionRevision
        FROM SessionRevision sessionRevision
        JOIN FETCH sessionRevision.content content
        JOIN FETCH sessionRevision.region
        JOIN FETCH sessionRevision.targetSession
        WHERE sessionRevision.sessionRevisionId = :revisionId
          AND content.deletedAt IS NULL
        """)
    Optional<SessionRevision> findReviewTargetByIdForUpdate(@Param("revisionId") Long revisionId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE SessionRevision sessionRevision
        SET sessionRevision.status = io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus.REJECTED,
            sessionRevision.reviewedAt = :reviewedAt,
            sessionRevision.reviewedBy = :reviewedBy,
            sessionRevision.rejectReason = :reason
        WHERE sessionRevision.sessionRevisionId = :revisionId
          AND sessionRevision.status = io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus.PENDING
        """)
    int rejectPendingById(
        @Param("revisionId") Long revisionId,
        @Param("reviewedBy") AppUser reviewedBy,
        @Param("reviewedAt") Instant reviewedAt,
        @Param("reason") String reason
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
