package io.regionevent.regioneventbackend.domain.mission.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;

@Service
public class MissionParticipationReadService {

    private final MissionParticipationRepository missionParticipationRepository;

    public MissionParticipationReadService(MissionParticipationRepository missionParticipationRepository) {
        this.missionParticipationRepository = missionParticipationRepository;
    }

    public Page<MissionParticipationSummary> findByUserIdAndStatus(
        Long userId,
        MissionParticipationStatus status,
        Pageable pageable
    ) {
        return missionParticipationRepository.findSummariesByUserIdAndStatus(userId, status, pageable)
            .map(MissionParticipationSummary::from);
    }
}
