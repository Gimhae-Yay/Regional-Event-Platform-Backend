package io.regionevent.regioneventbackend.domain.mission.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.mission.repository.MissionRewardClaimRepository;

@Service
public class MissionRewardClaimService {

    private final MissionRewardClaimRepository missionRewardClaimRepository;

    public MissionRewardClaimService(MissionRewardClaimRepository missionRewardClaimRepository) {
        this.missionRewardClaimRepository = missionRewardClaimRepository;
    }

    public boolean existsByParticipationId(Long participationId) {
        return missionRewardClaimRepository.existsByMissionParticipationMissionParticipationId(participationId);
    }
}
