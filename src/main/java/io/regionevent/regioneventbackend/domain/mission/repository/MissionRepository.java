package io.regionevent.regioneventbackend.domain.mission.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

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
    Optional<Mission> findMissionDetailByMissionId(@Param("missionId") Long missionId);

    @EntityGraph(attributePaths = {"region", "rewardCouponPolicy"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT mission
        FROM Mission mission
        WHERE mission.missionId = :missionId
        """)
    Optional<Mission> findByMissionIdForUpdate(@Param("missionId") Long missionId);
}
