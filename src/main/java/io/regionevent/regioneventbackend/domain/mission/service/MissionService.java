package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class MissionService {

    private final MissionRepository missionRepository;

    public MissionService(MissionRepository missionRepository) {
        this.missionRepository = missionRepository;
    }

    public Mission findOperatorMissionDetail(Long missionId) {
        return missionRepository.findOperatorMissionDetailByMissionId(missionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public Mission create(
        Region region,
        MissionConditionType conditionType,
        Integer requiredVisitCount,
        CouponPolicy rewardCouponPolicy,
        Instant endsAt
    ) {
        try {
            return new Mission(
                region,
                conditionType,
                requiredVisitCount,
                rewardCouponPolicy,
                endsAt
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }

    public Mission save(Mission mission) {
        return missionRepository.saveAndFlush(mission);
    }
}
