package io.regionevent.regioneventbackend.domain.content.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public interface ContentRepository extends JpaRepository<Content, Long> {

    @EntityGraph(attributePaths = "region")
    Optional<Content> findByContentId(Long contentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"operator", "region", "representativeImageObject"})
    Optional<Content> findByContentIdAndDeletedAtIsNull(Long contentId);

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
}
