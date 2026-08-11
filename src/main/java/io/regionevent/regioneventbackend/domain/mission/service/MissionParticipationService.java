package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class MissionParticipationService {

    private final MissionParticipationRepository missionParticipationRepository;

    public MissionParticipationService(MissionParticipationRepository missionParticipationRepository) {
        this.missionParticipationRepository = missionParticipationRepository;
    }

    public MissionParticipation create(
        Mission mission,
        AppUser user,
        Instant joinedAt
    ) {
        return missionParticipationRepository.saveAndFlush(new MissionParticipation(
            mission,
            user,
            joinedAt
        ));
    }

    public MissionParticipation findForUpdate(Long participationId) {
        return missionParticipationRepository.findByMissionParticipationIdForUpdate(participationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
