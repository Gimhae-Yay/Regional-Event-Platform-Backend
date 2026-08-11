package io.regionevent.regioneventbackend.domain.mission.repository;

import java.math.BigDecimal;
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
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;

public interface MissionRepository extends JpaRepository<Mission, Long> {

    Page<Mission> findAllByRegionRegionIdOrderByMissionIdDesc(Long regionId, Pageable pageable);

    Page<Mission> findAllByRegionRegionIdAndStatusOrderByMissionIdDesc(
        Long regionId,
        MissionStatus status,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"region", "rewardCouponPolicy"})
    Optional<Mission> findByMissionId(Long missionId);

    @Query("""
        SELECT mission.rewardCouponPolicy.couponPolicyId
        FROM Mission mission
        WHERE mission.missionId = :missionId
        """)
    Optional<Long> findRewardCouponPolicyIdByMissionId(@Param("missionId") Long missionId);

    @Query("""
        SELECT DISTINCT mission
        FROM Mission mission
        JOIN FETCH mission.region region
        JOIN FETCH mission.rewardCouponPolicy rewardCouponPolicy
        LEFT JOIN FETCH mission.targetContents targetContent
        LEFT JOIN FETCH targetContent.content content
        WHERE mission.missionId = :missionId
        """)
    Optional<Mission> findMissionDetailByMissionId(@Param("missionId") Long missionId);

    @Query("""
        SELECT DISTINCT mission
        FROM Mission mission
        JOIN FETCH mission.region region
        JOIN FETCH mission.rewardCouponPolicy rewardCouponPolicy
        LEFT JOIN FETCH mission.targetContents targetContent
        LEFT JOIN FETCH targetContent.content content
        WHERE mission.missionId = :missionId
          AND region.isPublic = true
          AND mission.status = io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus.PUBLISHED
          AND mission.endsAt > :now
        """)
    Optional<Mission> findPublicMissionDetailByMissionId(
        @Param("missionId") Long missionId,
        @Param("now") Instant now
    );

    @EntityGraph(attributePaths = {"region", "rewardCouponPolicy"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT mission
        FROM Mission mission
        WHERE mission.missionId = :missionId
        """)
    Optional<Mission> findByMissionIdForUpdate(@Param("missionId") Long missionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT mission
        FROM Mission mission
        WHERE mission.missionId = :missionId
        """)
    Optional<Mission> findByMissionIdForParticipationUpdate(@Param("missionId") Long missionId);

    @Query(value = "SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))", nativeQuery = true)
    BigDecimal findCurrentEpochSeconds();

    boolean existsByRewardCouponPolicyCouponPolicyIdAndStatus(
        Long couponPolicyId,
        MissionStatus status
    );

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
