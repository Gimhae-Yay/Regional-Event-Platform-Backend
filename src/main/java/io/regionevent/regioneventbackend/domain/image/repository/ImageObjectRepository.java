package io.regionevent.regioneventbackend.domain.image.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;

public interface ImageObjectRepository extends JpaRepository<ImageObject, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ImageObject> findByImageObjectId(Long imageObjectId);

    @Query("""
        SELECT imageObject.imageObjectId
        FROM ImageObject imageObject
        WHERE imageObject.lifecycleStatus = :activeStatus
            AND imageObject.linkedAt IS NULL
            AND imageObject.uploadExpiresAt <= CURRENT_TIMESTAMP
            AND NOT EXISTS (
                SELECT content.contentId
                FROM Content content
                WHERE content.representativeImageObject = imageObject
            )
            AND NOT EXISTS (
                SELECT contentRevision.contentRevisionId
                FROM ContentRevision contentRevision
                WHERE contentRevision.candidateImageObject = imageObject
            )
        ORDER BY imageObject.imageObjectId ASC
        """)
    List<Long> findExpiredUnlinkedUploadCandidateIdsWithoutDirectReferences(
        @Param("activeStatus") ImageLifecycleStatus activeStatus,
        Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE ImageObject imageObject
        SET imageObject.lifecycleStatus = :deletePendingStatus
        WHERE imageObject.imageObjectId = :imageObjectId
            AND imageObject.lifecycleStatus = :activeStatus
            AND imageObject.linkedAt IS NULL
            AND imageObject.uploadExpiresAt <= CURRENT_TIMESTAMP
            AND NOT EXISTS (
                SELECT content.contentId
                FROM Content content
                WHERE content.representativeImageObject = imageObject
            )
            AND NOT EXISTS (
                SELECT contentRevision.contentRevisionId
                FROM ContentRevision contentRevision
                WHERE contentRevision.candidateImageObject = imageObject
            )
        """)
    int markExpiredUnlinkedUploadCandidateDeletePending(
        @Param("imageObjectId") Long imageObjectId,
        @Param("activeStatus") ImageLifecycleStatus activeStatus,
        @Param("deletePendingStatus") ImageLifecycleStatus deletePendingStatus
    );

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE ImageObject imageObject
        SET imageObject.lifecycleStatus = :deletePendingStatus
        WHERE imageObject.imageObjectId = :imageObjectId
            AND imageObject.lifecycleStatus = :activeStatus
            AND NOT EXISTS (
                SELECT content.contentId
                FROM Content content
                WHERE content.representativeImageObject = imageObject
            )
            AND NOT EXISTS (
                SELECT contentRevision.contentRevisionId
                FROM ContentRevision contentRevision
                WHERE contentRevision.candidateImageObject = imageObject
            )
        """)
    int markActiveObjectDeletePendingWithoutDirectReferences(
        @Param("imageObjectId") Long imageObjectId,
        @Param("activeStatus") ImageLifecycleStatus activeStatus,
        @Param("deletePendingStatus") ImageLifecycleStatus deletePendingStatus
    );

    @Query("""
        SELECT imageObject
        FROM ImageObject imageObject
        WHERE imageObject.lifecycleStatus = :deletePendingStatus
            AND (
                imageObject.lastDeleteAttemptedAt IS NULL
                OR imageObject.lastDeleteAttemptedAt <= CURRENT_TIMESTAMP
            )
            AND NOT EXISTS (
                SELECT content.contentId
                FROM Content content
                WHERE content.representativeImageObject = imageObject
            )
            AND NOT EXISTS (
                SELECT contentRevision.contentRevisionId
                FROM ContentRevision contentRevision
                WHERE contentRevision.candidateImageObject = imageObject
            )
        ORDER BY imageObject.imageObjectId ASC
        """)
    List<ImageObject> findRetryableDeletePendingObjectsWithoutDirectReferences(
        @Param("deletePendingStatus") ImageLifecycleStatus deletePendingStatus,
        Pageable pageable
    );

    @Query(value = "SELECT CURRENT_TIMESTAMP", nativeQuery = true)
    Instant findCurrentTimestamp();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        DELETE FROM ImageObject imageObject
        WHERE imageObject.imageObjectId = :imageObjectId
            AND imageObject.lifecycleStatus = :deletePendingStatus
            AND NOT EXISTS (
                SELECT content.contentId
                FROM Content content
                WHERE content.representativeImageObject = imageObject
            )
            AND NOT EXISTS (
                SELECT contentRevision.contentRevisionId
                FROM ContentRevision contentRevision
                WHERE contentRevision.candidateImageObject = imageObject
            )
        """)
    int deleteDeletePendingObjectWithoutDirectReferences(
        @Param("imageObjectId") Long imageObjectId,
        @Param("deletePendingStatus") ImageLifecycleStatus deletePendingStatus
    );
}
