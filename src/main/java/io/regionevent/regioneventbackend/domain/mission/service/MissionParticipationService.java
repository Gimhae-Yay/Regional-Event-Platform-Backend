package io.regionevent.regioneventbackend.domain.mission.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;

@Service
public class MissionParticipationService {

    private final MissionParticipationRepository missionParticipationRepository;

    public MissionParticipationService(MissionParticipationRepository missionParticipationRepository) {
        this.missionParticipationRepository = missionParticipationRepository;
    }

    public void endInProgress(Long missionId) {
        List<MissionParticipation> participations = missionParticipationRepository
            .findAllByMissionIdAndStatusForUpdate(
                missionId,
                MissionParticipationStatus.IN_PROGRESS
            );
        participations.forEach(MissionParticipation::endIncomplete);
    }
}
