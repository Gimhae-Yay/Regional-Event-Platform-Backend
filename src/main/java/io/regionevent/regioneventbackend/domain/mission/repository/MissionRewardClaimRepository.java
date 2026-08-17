package io.regionevent.regioneventbackend.domain.mission.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionRewardClaim;

public interface MissionRewardClaimRepository extends JpaRepository<MissionRewardClaim, Long> {

    @EntityGraph(attributePaths = {
        "missionParticipation",
        "missionParticipation.mission",
        "missionParticipation.user",
        "couponPolicy",
        "couponPolicy.region"
    })
    Optional<MissionRewardClaim> findByMissionParticipationMissionParticipationId(
        Long missionParticipationId
    );

    boolean existsByMissionParticipationMissionParticipationId(Long missionParticipationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT claim
        FROM MissionRewardClaim claim
        WHERE claim.missionParticipation.missionParticipationId = :missionParticipationId
        """)
    Optional<MissionRewardClaim> findByMissionParticipationIdForUpdate(
        @Param("missionParticipationId") Long missionParticipationId
    );
}
