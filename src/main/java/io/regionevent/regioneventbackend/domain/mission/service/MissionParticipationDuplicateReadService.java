package io.regionevent.regioneventbackend.domain.mission.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class MissionParticipationDuplicateReadService {

    private final MissionParticipationRepository missionParticipationRepository;

    public MissionParticipationDuplicateReadService(
        MissionParticipationRepository missionParticipationRepository
    ) {
        this.missionParticipationRepository = missionParticipationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<MissionParticipation> find(
        Long missionId,
        Long userId
    ) {
        Optional<MissionParticipation> participation = missionParticipationRepository
            .findByMissionMissionIdAndUserUserId(missionId, userId);
        participation.ifPresent(existingParticipation -> {
            if (!existingParticipation.getMission().getRegion().isPublic()) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
        });
        return participation;
    }
}
