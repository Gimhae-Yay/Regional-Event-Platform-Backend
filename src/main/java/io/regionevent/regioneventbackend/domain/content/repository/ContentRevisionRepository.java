package io.regionevent.regioneventbackend.domain.content.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;

public interface ContentRevisionRepository extends JpaRepository<ContentRevision, Long> {

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
}
