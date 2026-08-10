package io.regionevent.regioneventbackend.domain.mission.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionProgress;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionProgressRepository;

@Service
public class MissionProgressService {

    private final MissionProgressRepository missionProgressRepository;

    public MissionProgressService(MissionProgressRepository missionProgressRepository) {
        this.missionProgressRepository = missionProgressRepository;
    }

    public List<MissionProgress> findAllByParticipationId(Long participationId) {
        return missionProgressRepository.findAllByMissionParticipationIdOrderByRecordedAtAsc(participationId);
    }
}
