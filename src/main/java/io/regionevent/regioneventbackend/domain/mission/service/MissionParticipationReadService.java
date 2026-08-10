package io.regionevent.regioneventbackend.domain.mission.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class MissionParticipationReadService {

    private final MissionParticipationRepository missionParticipationRepository;

    public MissionParticipationReadService(MissionParticipationRepository missionParticipationRepository) {
        this.missionParticipationRepository = missionParticipationRepository;
    }

    public MissionParticipation findDetail(Long participationId) {
        return missionParticipationRepository.findByMissionParticipationId(participationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
