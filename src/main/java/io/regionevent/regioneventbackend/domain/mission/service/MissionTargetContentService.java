package io.regionevent.regioneventbackend.domain.mission.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.mission.repository.MissionTargetContentRepository;

@Service
public class MissionTargetContentService {

    private final MissionTargetContentRepository missionTargetContentRepository;

    public MissionTargetContentService(MissionTargetContentRepository missionTargetContentRepository) {
        this.missionTargetContentRepository = missionTargetContentRepository;
    }

    public long countByMissionId(Long missionId) {
        return missionTargetContentRepository.countByMissionMissionId(missionId);
    }
}
