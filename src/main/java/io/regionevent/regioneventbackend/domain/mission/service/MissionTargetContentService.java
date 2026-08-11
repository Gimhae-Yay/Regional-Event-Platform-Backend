package io.regionevent.regioneventbackend.domain.mission.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    public List<Long> findContentIdsOrderByContentId(Long missionId) {
        return missionTargetContentRepository.findContentIdsByMissionIdOrderByContentIdAsc(missionId);
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public boolean contains(
        Long missionId,
        Long contentId
    ) {
        return missionTargetContentRepository.existsByMissionMissionIdAndContentContentId(
            missionId,
            contentId
        );
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public long countRequiredContents(Long missionId) {
        return missionTargetContentRepository.countByMissionMissionId(missionId);
    }
}
