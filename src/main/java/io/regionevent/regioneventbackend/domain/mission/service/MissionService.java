package io.regionevent.regioneventbackend.domain.mission.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class MissionService {

    private final MissionRepository missionRepository;

    public MissionService(MissionRepository missionRepository) {
        this.missionRepository = missionRepository;
    }

    public Mission findMissionDetail(Long missionId) {
        return missionRepository.findMissionDetailByMissionId(missionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
