package io.regionevent.regioneventbackend.domain.mission.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.mission.repository.MissionRewardClaimRepository;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionRewardClaim;

import java.util.Optional;

@Service
public class MissionRewardClaimService {

    private final MissionRewardClaimRepository missionRewardClaimRepository;

    public MissionRewardClaimService(MissionRewardClaimRepository missionRewardClaimRepository) {
        this.missionRewardClaimRepository = missionRewardClaimRepository;
    }

    public boolean existsByParticipationId(Long participationId) {
        return missionRewardClaimRepository.existsByMissionParticipationMissionParticipationId(participationId);
    }

    public Optional<MissionRewardClaim> findByParticipationId(Long participationId) {
        return missionRewardClaimRepository.findByMissionParticipationMissionParticipationId(participationId);
    }

    public Optional<MissionRewardClaim> findByParticipationIdForUpdate(Long participationId) {
        return missionRewardClaimRepository.findByMissionParticipationIdForUpdate(participationId);
    }

    public MissionRewardClaim create(MissionRewardClaim missionRewardClaim) {
        return missionRewardClaimRepository.saveAndFlush(missionRewardClaim);
    }
}
