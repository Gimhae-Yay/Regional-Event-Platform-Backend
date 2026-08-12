package io.regionevent.regioneventbackend.domain.mission.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.mission.repository.MissionProgressRepository;

@Service
public class MissionProgressDuplicateReadService {

    private final MissionProgressRepository missionProgressRepository;

    public MissionProgressDuplicateReadService(MissionProgressRepository missionProgressRepository) {
        this.missionProgressRepository = missionProgressRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean exists(
        Long participationId,
        Long visitId
    ) {
        return missionProgressRepository.existsByMissionParticipationMissionParticipationIdAndVisitVisitId(
            participationId,
            visitId
        );
    }
}
