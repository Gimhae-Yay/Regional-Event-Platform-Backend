package io.regionevent.regioneventbackend.domain.mission.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

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

    public MissionParticipation findDetail(Long participationId) {
        return missionParticipationRepository.findByMissionParticipationId(participationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public Optional<MissionParticipationSummary> findSummaryByMissionIdAndUserId(
        Long missionId,
        Long userId
    ) {
        return missionParticipationRepository.findSummaryByMissionIdAndUserId(missionId, userId)
            .map(MissionParticipationSummary::from);
    }

    public Optional<MissionParticipation> findByMissionIdAndUserId(
        Long missionId,
        Long userId
    ) {
        return missionParticipationRepository.findByMissionMissionIdAndUserUserId(missionId, userId);
    }
}
