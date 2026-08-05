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

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;

public interface ContentRepository extends JpaRepository<Content, Long> {

    boolean existsByOperatorUserId(Long userId);

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.content.repository.PublicContentProjection(
            content.region.regionId,
            content.contentId,
            content.versionNo,
            content.contentType,
            content.title,
            content.description,
            content.locationText,
            content.operatingHoursText,
            content.precautions,
            content.ageRequirement,
            content.materials,
            content.cancellationPolicyText,
            representativeImageObject,
            content.representativeImageAssignedAt,
            CASE WHEN EXISTS (
                SELECT contentSession.sessionId
                FROM ContentSession contentSession
                WHERE contentSession.content = content
                    AND contentSession.status = :sessionStatus
                    AND contentSession.startsAt > CURRENT_TIMESTAMP
                    AND contentSession.remainingCapacity > 0
            ) THEN true ELSE false END
        )
        FROM Content content
        LEFT JOIN content.representativeImageObject representativeImageObject
        WHERE content.region.regionId = :regionId
            AND content.status = :contentStatus
            AND content.deletedAt IS NULL
            AND (:contentType IS NULL OR content.contentType = :contentType)
            AND (
                :reservationAvailable IS NULL
                OR (
                    :reservationAvailable = true
                    AND EXISTS (
                        SELECT contentSession.sessionId
                        FROM ContentSession contentSession
                        WHERE contentSession.content = content
                            AND contentSession.status = :sessionStatus
                            AND contentSession.startsAt > CURRENT_TIMESTAMP
                            AND contentSession.remainingCapacity > 0
                    )
                )
                OR (
                    :reservationAvailable = false
                    AND NOT EXISTS (
                        SELECT contentSession.sessionId
                        FROM ContentSession contentSession
                        WHERE contentSession.content = content
                            AND contentSession.status = :sessionStatus
                            AND contentSession.startsAt > CURRENT_TIMESTAMP
                            AND contentSession.remainingCapacity > 0
                    )
                )
            )
        ORDER BY content.publishAt DESC, content.contentId DESC
        """)
    List<PublicContentProjection> findPublicContents(
        @Param("regionId") Long regionId,
        @Param("contentType") ContentType contentType,
        @Param("reservationAvailable") Boolean reservationAvailable,
        @Param("contentStatus") ContentStatus contentStatus,
        @Param("sessionStatus") ContentSessionStatus sessionStatus
    );

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.content.repository.MyContentProjection(
            content.contentId,
            content.contentType,
            content.title,
            content.status,
            content.createdAt
        )
        FROM Content content
        WHERE content.operator.userId = :operatorUserId
            AND content.region.regionId = :regionId
            AND content.deletedAt IS NULL
        ORDER BY content.createdAt DESC, content.contentId DESC
        """)
    List<MyContentProjection> findMyContents(
        @Param("operatorUserId") Long operatorUserId,
        @Param("regionId") Long regionId
    );

    @EntityGraph(attributePaths = "region")
    Optional<Content> findByContentId(Long contentId);

    @EntityGraph(attributePaths = {"operator", "region"})
    @Query("""
        SELECT content
        FROM Content content
        WHERE content.contentId = :contentId
            AND content.deletedAt IS NULL
        """)
    Optional<Content> findOperatorReservationListTarget(@Param("contentId") Long contentId);

    @EntityGraph(attributePaths = {"region", "representativeImageObject"})
    @Query("""
        SELECT content
        FROM Content content
        WHERE content.contentId = :contentId
            AND content.status = :status
            AND content.deletedAt IS NULL
            AND content.region.isPublic = true
        """)
    Optional<Content> findPublicContentByContentId(
        @Param("contentId") Long contentId,
        @Param("status") ContentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"operator", "region", "representativeImageObject"})
    Optional<Content> findByContentIdAndDeletedAtIsNull(Long contentId);

    @EntityGraph(attributePaths = {"operator", "region", "representativeImageObject"})
    @Query("""
        SELECT content
        FROM Content content
        WHERE content.contentId = :contentId
            AND content.deletedAt IS NULL
        """)
    Optional<Content> findDetailByContentIdAndDeletedAtIsNull(@Param("contentId") Long contentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"region", "representativeImageObject"})
    @Query("""
        SELECT content
        FROM Content content
        WHERE content.contentId = :contentId
        """)
    Optional<Content> findDeletionTargetForUpdate(@Param("contentId") Long contentId);

    boolean existsByContentIdAndStatusAndDeletedAtIsNull(
        Long contentId,
        ContentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "region")
    @Query("""
        SELECT content
        FROM Content content
        WHERE content.contentId = :contentId
            AND content.deletedAt IS NULL
        """)
    Optional<Content> findApprovalTargetForUpdate(@Param("contentId") Long contentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "region")
    @Query("""
        SELECT content
        FROM Content content
        WHERE content.contentId = :contentId
            AND content.deletedAt IS NULL
        """)
    Optional<Content> findEndTargetForUpdate(@Param("contentId") Long contentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "region")
    @Query("""
        SELECT content
        FROM Content content
        WHERE content.contentId = :contentId
            AND content.deletedAt IS NULL
        """)
    Optional<Content> findSuspendTargetForUpdate(@Param("contentId") Long contentId);

    @Query(value = """
        SELECT content_id
        FROM content
        WHERE content_id = :contentId
            AND status = 'PUBLISHED'
            AND deleted_at IS NULL
        FOR UPDATE
        """, nativeQuery = true)
    Optional<Long> findPublishedReservationTargetIdForUpdate(@Param("contentId") Long contentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Content content
        SET content.status = :nextStatus,
            content.versionNo = content.versionNo + 1,
            content.updatedAt = :updatedAt
        WHERE content.contentId = :contentId
            AND content.status = :expectedStatus
            AND content.deletedAt IS NULL
        """)
    int updateStatusIfExpected(
        @Param("contentId") Long contentId,
        @Param("expectedStatus") ContentStatus expectedStatus,
        @Param("nextStatus") ContentStatus nextStatus,
        @Param("updatedAt") Instant updatedAt
    );

    default int rejectPendingByContentId(Long contentId, Instant rejectedAt) {
        return updateStatusIfExpected(
            contentId,
            ContentStatus.PENDING,
            ContentStatus.REJECTED,
            rejectedAt
        );
    }

    default int submitRejectedByContentId(Long contentId, Instant submittedAt) {
        return updateStatusIfExpected(
            contentId,
            ContentStatus.REJECTED,
            ContentStatus.PENDING,
            submittedAt
        );
    }

    default int endPublishedByContentId(Long contentId, Instant endedAt) {
        return updateStatusIfExpected(
            contentId,
            ContentStatus.PUBLISHED,
            ContentStatus.ENDED,
            endedAt
        );
    }

    default int suspendPublishedByContentId(Long contentId, Instant suspendedAt) {
        return updateStatusIfExpected(
            contentId,
            ContentStatus.PUBLISHED,
            ContentStatus.SUSPENDED,
            suspendedAt
        );
    }
}
