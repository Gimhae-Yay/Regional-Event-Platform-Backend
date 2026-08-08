package io.regionevent.regioneventbackend.domain.mission.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;

public interface MissionParticipationRepository extends JpaRepository<MissionParticipation, Long> {

    @EntityGraph(attributePaths = {"mission", "user"})
    Optional<MissionParticipation> findByMissionMissionIdAndUserUserId(
        Long missionId,
        Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT participation
        FROM MissionParticipation participation
        WHERE participation.mission.missionId = :missionId
          AND participation.user.userId = :userId
        """)
    Optional<MissionParticipation> findByMissionIdAndUserIdForUpdate(
        @Param("missionId") Long missionId,
        @Param("userId") Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT participation
        FROM MissionParticipation participation
        WHERE participation.missionParticipationId = :missionParticipationId
        """)
    Optional<MissionParticipation> findByMissionParticipationIdForUpdate(
        @Param("missionParticipationId") Long missionParticipationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT participation
        FROM MissionParticipation participation
        WHERE participation.mission.missionId = :missionId
          AND participation.status = :status
        ORDER BY participation.missionParticipationId ASC
        """)
    List<MissionParticipation> findAllByMissionIdAndStatusForUpdate(
        @Param("missionId") Long missionId,
        @Param("status") MissionParticipationStatus status
    );

    @Query("""
        SELECT participation
        FROM MissionParticipation participation
        WHERE participation.user.userId = :userId
          AND participation.mission.region.regionId = :regionId
          AND participation.status = :status
        ORDER BY participation.missionParticipationId ASC
        """)
    List<MissionParticipation> findAllByUserIdAndRegionIdAndStatus(
        @Param("userId") Long userId,
        @Param("regionId") Long regionId,
        @Param("status") MissionParticipationStatus status
    );
}
