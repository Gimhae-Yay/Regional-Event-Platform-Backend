package io.regionevent.regioneventbackend.domain.mission.repository;

import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @EntityGraph(attributePaths = {"mission", "content"})
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
}
