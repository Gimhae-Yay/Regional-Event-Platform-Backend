package io.regionevent.regioneventbackend.domain.mission.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionProgress;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionProgressId;

public interface MissionProgressRepository extends JpaRepository<MissionProgress, MissionProgressId> {

    boolean existsByMissionParticipationMissionParticipationIdAndVisitVisitId(
        Long missionParticipationId,
        Long visitId
    );

    boolean existsByMissionParticipationMissionParticipationIdAndContentContentId(
        Long missionParticipationId,
        Long contentId
    );

    long countByMissionParticipationMissionParticipationId(Long missionParticipationId);

    @EntityGraph(attributePaths = {"visit", "content"})
    @Query("""
        SELECT progress
        FROM MissionProgress progress
        WHERE progress.missionParticipation.missionParticipationId = :missionParticipationId
        ORDER BY progress.recordedAt ASC, progress.visit.visitId ASC
        """)
    List<MissionProgress> findAllByMissionParticipationIdOrderByRecordedAtAsc(
        @Param("missionParticipationId") Long missionParticipationId
    );
}
