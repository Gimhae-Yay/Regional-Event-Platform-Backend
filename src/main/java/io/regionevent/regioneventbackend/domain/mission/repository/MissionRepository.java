package io.regionevent.regioneventbackend.domain.mission.repository;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;

public interface MissionRepository extends JpaRepository<Mission, Long> {

    @EntityGraph(attributePaths = {"region", "rewardCouponPolicy"})
    Optional<Mission> findByMissionId(Long missionId);

    @Query("""
        SELECT DISTINCT mission
        FROM Mission mission
        JOIN FETCH mission.region region
        JOIN FETCH mission.rewardCouponPolicy rewardCouponPolicy
        LEFT JOIN FETCH mission.targetContents targetContent
        LEFT JOIN FETCH targetContent.content content
        WHERE mission.missionId = :missionId
        """)
    Optional<Mission> findOperatorMissionDetailByMissionId(@Param("missionId") Long missionId);

    @EntityGraph(attributePaths = {"region", "rewardCouponPolicy"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT mission
        FROM Mission mission
        WHERE mission.missionId = :missionId
        """)
    Optional<Mission> findByMissionIdForUpdate(@Param("missionId") Long missionId);

    @Query(
        value = """
            SELECT mission.missionId AS missionId,
                   region.regionId AS regionId,
                   mission.conditionType AS conditionType,
                   mission.requiredVisitCount AS requiredVisitCount,
                   SUM(CASE WHEN targetContent.content IS NULL THEN 0L ELSE 1L END) AS targetContentCount,
                   mission.endsAt AS endsAt,
                   participation.status AS participationStatus
            FROM Mission mission
            JOIN mission.region region
            LEFT JOIN mission.targetContents targetContent
            LEFT JOIN MissionParticipation participation
              ON participation.mission = mission
             AND :userId IS NOT NULL
             AND participation.user.userId = :userId
            WHERE region.regionId = :regionId
              AND mission.status = io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus.PUBLISHED
              AND mission.endsAt > :now
            GROUP BY mission.missionId,
                     region.regionId,
                     mission.conditionType,
                     mission.requiredVisitCount,
                     mission.endsAt,
                     participation.status
            ORDER BY mission.endsAt ASC, mission.missionId ASC
            """,
        countQuery = """
            SELECT COUNT(mission)
            FROM Mission mission
            WHERE mission.region.regionId = :regionId
              AND mission.status = io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus.PUBLISHED
              AND mission.endsAt > :now
            """
    )
    Page<PublicRegionMissionProjection> findPublicRegionMissions(
        @Param("regionId") Long regionId,
        @Param("userId") Long userId,
        @Param("now") Instant now,
        Pageable pageable
    );
}
