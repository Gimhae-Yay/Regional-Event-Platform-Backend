package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreateMissionParticipationUseCase {

    private final MissionParticipationDuplicateReadService missionParticipationDuplicateReadService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final MissionService missionService;
    private final RegionService regionService;
    private final MissionParticipationReadService missionParticipationReadService;
    private final MissionParticipationService missionParticipationService;
    private final TransactionTemplate transactionTemplate;

    public CreateMissionParticipationUseCase(
        MissionParticipationDuplicateReadService missionParticipationDuplicateReadService,
        UserRoleAssignmentService userRoleAssignmentService,
        MissionService missionService,
        RegionService regionService,
        MissionParticipationReadService missionParticipationReadService,
        MissionParticipationService missionParticipationService,
        PlatformTransactionManager transactionManager
    ) {
        this.missionParticipationDuplicateReadService = missionParticipationDuplicateReadService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.missionService = missionService;
        this.regionService = regionService;
        this.missionParticipationReadService = missionParticipationReadService;
        this.missionParticipationService = missionParticipationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public CreateMissionParticipationResult create(
        Long userId,
        Long missionId
    ) {
        AppUser user = userRoleAssignmentService.findActiveVisitor(userId).getAppUser();
        Mission mission = missionService.findMission(missionId);
        validatePublicRegion(mission.getRegion());

        MissionParticipation existingParticipation = missionParticipationReadService
            .findByMissionIdAndUserId(missionId, userId)
            .orElse(null);
        if (existingParticipation != null) {
            return CreateMissionParticipationResult.from(existingParticipation);
        }

        try {
            return transactionTemplate.execute(status -> createNewParticipation(missionId, user));
        } catch (DataIntegrityViolationException exception) {
            return findDuplicateParticipation(missionId, userId, exception);
        }
    }

    private CreateMissionParticipationResult createNewParticipation(
        Long missionId,
        AppUser user
    ) {
        Mission mission = missionService.findMissionForParticipationUpdate(missionId);
        Region region = regionService.findRegionForUpdate(mission.getRegion().getRegionId());
        Instant operationAt = missionService.findCurrentDatabaseTime();
        validatePublicRegion(region);
        validateMission(mission, operationAt);
        return CreateMissionParticipationResult.from(
            missionParticipationService.create(mission, user, operationAt)
        );
    }

    private CreateMissionParticipationResult findDuplicateParticipation(
        Long missionId,
        Long userId,
        DataIntegrityViolationException exception
    ) {
        return missionParticipationDuplicateReadService.find(missionId, userId)
            .map(CreateMissionParticipationResult::from)
            .orElseThrow(() -> exception);
    }

    private void validatePublicRegion(Region region) {
        if (!region.isPublic()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private void validateMission(
        Mission mission,
        Instant operationAt
    ) {
        if (mission.getStatus() != MissionStatus.PUBLISHED || !mission.getEndsAt().isAfter(operationAt)) {
            throw new BusinessException(ErrorCode.MISSION_STATE_CONFLICT);
        }
    }
}
