package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
import io.regionevent.regioneventbackend.domain.mission.repository.PublicRegionMissionProjection;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
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

    public Mission findMission(Long missionId) {
        return missionRepository.findByMissionId(missionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public Mission findForUpdate(Long missionId) {
        return missionRepository.findByMissionIdForUpdate(missionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public Mission approve(
        Mission mission,
        Instant publishedAt
    ) {
        if (mission.getStatus() != MissionStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.MISSION_STATE_CONFLICT);
        }
        if (publishedAt == null || !publishedAt.isBefore(mission.getEndsAt())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        mission.approve(publishedAt);
        return missionRepository.saveAndFlush(mission);
    }

    public boolean existsPublishedRewardCouponPolicy(Long couponPolicyId) {
        return missionRepository.existsByRewardCouponPolicyCouponPolicyIdAndStatus(
            couponPolicyId,
            MissionStatus.PUBLISHED
        );
    }

    public PublicRegionMissionListResult findPublicRegionMissions(
        Long regionId,
        Long userId,
        Instant now,
        Pageable pageable
    ) {
        Page<PublicRegionMissionProjection> missions = missionRepository.findPublicRegionMissions(
            regionId,
            userId,
            now,
            pageable
        );
        List<PublicRegionMissionListResult.Mission> content = missions.getContent().stream()
            .map(mission -> new PublicRegionMissionListResult.Mission(
                mission.getMissionId(),
                mission.getRegionId(),
                mission.getConditionType(),
                mission.getRequiredVisitCount(),
                Math.toIntExact(mission.getTargetContentCount()),
                mission.getEndsAt(),
                mission.getParticipationStatus()
            ))
            .toList();
        return new PublicRegionMissionListResult(
            content,
            missions.getNumber(),
            missions.getSize(),
            missions.getTotalElements(),
            missions.getTotalPages()
        );
    }
}
