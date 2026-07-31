package io.regionevent.regioneventbackend.domain.image.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;

public interface ImageObjectRepository extends JpaRepository<ImageObject, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT imageObject
        FROM ImageObject imageObject
        LEFT JOIN FETCH imageObject.createdByUser
        LEFT JOIN FETCH imageObject.region
        WHERE imageObject.imageObjectId = :imageObjectId
        """)
    Optional<ImageObject> findByIdForUpdate(@Param("imageObjectId") Long imageObjectId);

    @Query("""
        SELECT imageObject.imageObjectId
        FROM ImageObject imageObject
        WHERE (
            imageObject.lifecycleStatus = :activeStatus
            AND imageObject.linkedAt IS NULL
            AND imageObject.uploadExpiresAt <= :now
          ) OR imageObject.lifecycleStatus = :deletePendingStatus
        ORDER BY imageObject.lastDeleteAttemptedAt ASC, imageObject.imageObjectId ASC
        """)
    List<Long> findUploadCleanupCandidateIds(
        @Param("activeStatus") ImageLifecycleStatus activeStatus,
        @Param("deletePendingStatus") ImageLifecycleStatus deletePendingStatus,
        @Param("now") Instant now
    );
}
