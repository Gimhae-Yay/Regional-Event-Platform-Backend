package io.regionevent.regioneventbackend.domain.mission.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public boolean existsByVisitId(
        Long participationId,
        Long visitId
    ) {
        return missionProgressRepository.existsByMissionParticipationMissionParticipationIdAndVisitVisitId(
            participationId,
            visitId
        );
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public boolean existsByContentId(
        Long participationId,
        Long contentId
    ) {
        return missionProgressRepository.existsByMissionParticipationMissionParticipationIdAndContentContentId(
            participationId,
            contentId
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MissionProgress create(MissionProgress missionProgress) {
        return missionProgressRepository.saveAndFlush(missionProgress);
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public long countByParticipationId(Long participationId) {
        return missionProgressRepository.countByMissionParticipationMissionParticipationId(participationId);
    }
}
