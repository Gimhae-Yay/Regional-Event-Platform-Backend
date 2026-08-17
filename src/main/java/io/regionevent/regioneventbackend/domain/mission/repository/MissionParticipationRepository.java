package io.regionevent.regioneventbackend.domain.mission.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;

public interface MissionParticipationRepository extends JpaRepository<MissionParticipation, Long> {

    @Query(
        value = """
            SELECT participation.missionParticipationId AS participationId,
                mission.missionId AS missionId,
                participation.status AS status,
                COUNT(DISTINCT CASE
                    WHEN mission.conditionType = io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType.VISIT_COUNT
                        THEN progress.visit.visitId
                    ELSE progress.content.contentId
                END) AS progressCount,
                CASE
                    WHEN mission.conditionType = io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType.VISIT_COUNT
                        THEN mission.requiredVisitCount
                    ELSE COUNT(DISTINCT targetContent.content.contentId)
                END AS requiredCount,
                CASE WHEN COUNT(rewardClaim.missionRewardClaimId) > 0 THEN true ELSE false END AS rewardClaimed,
                participation.joinedAt AS joinedAt,
                participation.completedAt AS completedAt
            FROM MissionParticipation participation
            JOIN participation.mission mission
            LEFT JOIN MissionProgress progress
                ON progress.missionParticipation = participation
            LEFT JOIN mission.targetContents targetContent
            LEFT JOIN MissionRewardClaim rewardClaim
                ON rewardClaim.missionParticipation = participation
            WHERE participation.user.userId = :userId
              AND (:status IS NULL OR participation.status = :status)
            GROUP BY participation.missionParticipationId,
                mission.missionId,
                participation.status,
                mission.conditionType,
                mission.requiredVisitCount,
                participation.joinedAt,
                participation.completedAt
            ORDER BY participation.joinedAt DESC, participation.missionParticipationId DESC
            """,
        countQuery = """
            SELECT COUNT(participation)
            FROM MissionParticipation participation
            WHERE participation.user.userId = :userId
              AND (:status IS NULL OR participation.status = :status)
            """
    )
    Page<MissionParticipationSummaryProjection> findSummariesByUserIdAndStatus(
        @Param("userId") Long userId,
        @Param("status") MissionParticipationStatus status,
        Pageable pageable
    );

    @Query("""
        SELECT participation.missionParticipationId AS participationId,
            mission.missionId AS missionId,
            participation.status AS status,
            COUNT(DISTINCT CASE
                WHEN mission.conditionType = io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType.VISIT_COUNT
                    THEN progress.visit.visitId
                ELSE progress.content.contentId
            END) AS progressCount,
            CASE
                WHEN mission.conditionType = io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType.VISIT_COUNT
                    THEN mission.requiredVisitCount
                ELSE COUNT(DISTINCT targetContent.content.contentId)
            END AS requiredCount,
            CASE WHEN COUNT(rewardClaim.missionRewardClaimId) > 0 THEN true ELSE false END AS rewardClaimed,
            participation.joinedAt AS joinedAt,
            participation.completedAt AS completedAt
        FROM MissionParticipation participation
        JOIN participation.mission mission
        LEFT JOIN MissionProgress progress
            ON progress.missionParticipation = participation
        LEFT JOIN mission.targetContents targetContent
        LEFT JOIN MissionRewardClaim rewardClaim
            ON rewardClaim.missionParticipation = participation
        WHERE mission.missionId = :missionId
          AND participation.user.userId = :userId
        GROUP BY participation.missionParticipationId,
            mission.missionId,
            participation.status,
            mission.conditionType,
            mission.requiredVisitCount,
            participation.joinedAt,
            participation.completedAt
        """)
    Optional<MissionParticipationSummaryProjection> findSummaryByMissionIdAndUserId(
        @Param("missionId") Long missionId,
        @Param("userId") Long userId
    );

    @EntityGraph(attributePaths = {"mission", "user"})
    Optional<MissionParticipation> findByMissionMissionIdAndUserUserId(
        Long missionId,
        Long userId
    );

    @EntityGraph(attributePaths = {"mission", "mission.region", "user"})
    Optional<MissionParticipation> findByMissionParticipationId(Long missionParticipationId);

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
