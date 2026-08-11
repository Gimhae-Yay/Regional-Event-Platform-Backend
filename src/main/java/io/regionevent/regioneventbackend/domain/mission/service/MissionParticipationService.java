package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

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

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<MissionParticipation> findByIdForProgressUpdate(Long participationId) {
        return missionParticipationRepository.findByMissionParticipationIdForUpdate(participationId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(
        MissionParticipation participation,
        Instant completedAt
    ) {
        participation.complete(completedAt);
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
