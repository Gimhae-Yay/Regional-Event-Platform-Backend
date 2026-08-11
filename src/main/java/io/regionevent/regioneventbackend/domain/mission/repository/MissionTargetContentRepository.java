package io.regionevent.regioneventbackend.domain.mission.repository;

import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionTargetContent;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionTargetContentId;

public interface MissionTargetContentRepository
    extends JpaRepository<MissionTargetContent, MissionTargetContentId> {

    @EntityGraph(attributePaths = {"mission", "content"})
    @Query("""
        SELECT missionTargetContent
        FROM MissionTargetContent missionTargetContent
        WHERE missionTargetContent.mission.missionId = :missionId
        ORDER BY missionTargetContent.content.contentId ASC
        """)
    List<MissionTargetContent> findAllByMissionIdOrderByContentIdAsc(@Param("missionId") Long missionId);

    long countByMissionMissionId(Long missionId);

    @Query("""
        SELECT missionTargetContent.content.contentId
        FROM MissionTargetContent missionTargetContent
        WHERE missionTargetContent.mission.missionId = :missionId
        ORDER BY missionTargetContent.content.contentId ASC
        """)
    List<Long> findContentIdsByMissionIdOrderByContentIdAsc(@Param("missionId") Long missionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT missionTargetContent
        FROM MissionTargetContent missionTargetContent
        WHERE missionTargetContent.mission.missionId = :missionId
        ORDER BY missionTargetContent.content.contentId ASC
        """)
    List<MissionTargetContent> findAllByMissionIdForUpdateOrderByContentIdAsc(
        @Param("missionId") Long missionId
    );

    @Modifying(flushAutomatically = true)
    @Query("""
        DELETE FROM MissionTargetContent missionTargetContent
        WHERE missionTargetContent.mission.missionId = :missionId
        """)
    int deleteAllByMissionId(@Param("missionId") Long missionId);
}
