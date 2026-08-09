package io.regionevent.regioneventbackend.domain.mission.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionProgress;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetMyMissionParticipationUseCase {

    private final UserRoleAssignmentService userRoleAssignmentService;
    private final MissionParticipationReadService missionParticipationReadService;
    private final MissionProgressService missionProgressService;
    private final MissionRewardClaimService missionRewardClaimService;
    private final MissionTargetContentService missionTargetContentService;

    public GetMyMissionParticipationUseCase(
        UserRoleAssignmentService userRoleAssignmentService,
        MissionParticipationReadService missionParticipationReadService,
        MissionProgressService missionProgressService,
        MissionRewardClaimService missionRewardClaimService,
        MissionTargetContentService missionTargetContentService
    ) {
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.missionParticipationReadService = missionParticipationReadService;
        this.missionProgressService = missionProgressService;
        this.missionRewardClaimService = missionRewardClaimService;
        this.missionTargetContentService = missionTargetContentService;
    }

    @Transactional(readOnly = true)
    public MissionParticipationDetailResult get(Long userId, Long participationId) {
        UserRoleAssignment visitor = userRoleAssignmentService.findActiveVisitor(userId);
        MissionParticipation participation = missionParticipationReadService.findDetail(participationId);
        if (!visitor.getAppUser().getUserId().equals(participation.getUser().getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        List<MissionProgress> progresses = missionProgressService.findAllByParticipationId(
            participation.getMissionParticipationId()
        );
        Mission mission = participation.getMission();
        List<MissionProgress> visibleProgresses = resolveVisibleProgresses(mission, progresses);
        return new MissionParticipationDetailResult(
            participation,
            visibleProgresses,
            visibleProgresses.size(),
            resolveRequiredCount(mission),
            missionRewardClaimService.existsByParticipationId(participation.getMissionParticipationId())
        );
    }

    private List<MissionProgress> resolveVisibleProgresses(
        Mission mission,
        List<MissionProgress> progresses
    ) {
        if (mission.getConditionType() != MissionConditionType.CONTENT_SET) {
            return progresses;
        }
        Map<Long, MissionProgress> firstProgressByContentId = new LinkedHashMap<>();
        for (MissionProgress progress : progresses) {
            firstProgressByContentId.putIfAbsent(progress.getContent().getContentId(), progress);
        }
        return List.copyOf(firstProgressByContentId.values());
    }

    private int resolveRequiredCount(Mission mission) {
        if (mission.getConditionType() == MissionConditionType.CONTENT_SET) {
            return Math.toIntExact(missionTargetContentService.countByMissionId(mission.getMissionId()));
        }
        return mission.getRequiredVisitCount();
    }
}
