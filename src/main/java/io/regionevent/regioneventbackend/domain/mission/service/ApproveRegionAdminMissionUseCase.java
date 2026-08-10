package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionTargetContent;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService.AuthorizedRegionAdmin;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ApproveRegionAdminMissionUseCase {

    private static final String APPROVAL_REASON_CODE = "MISSION_APPROVED";

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final CouponPolicyService couponPolicyService;
    private final MissionService missionService;
    private final RegionService regionService;
    private final MissionTargetContentService missionTargetContentService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Clock clock;

    public ApproveRegionAdminMissionUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        CouponPolicyService couponPolicyService,
        MissionService missionService,
        RegionService regionService,
        MissionTargetContentService missionTargetContentService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        Clock clock
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.couponPolicyService = couponPolicyService;
        this.missionService = missionService;
        this.regionService = regionService;
        this.missionTargetContentService = missionTargetContentService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public ApproveRegionAdminMissionResult approve(
        Long userId,
        Long missionId,
        UUID requestId
    ) {
        AuthorizedRegionAdmin regionAdmin = regionAdminAuthorizationService
            .requireAuthorizedRegionAdmin(userId);
        Mission mission = missionService.findMission(missionId);

        try {
            validateRegionScope(regionAdmin, mission);
            Long initiallyReferencedCouponPolicyId = mission.getRewardCouponPolicy()
                .getCouponPolicyId();
            CouponPolicy rewardCouponPolicy = couponPolicyService.findForUpdate(
                initiallyReferencedCouponPolicyId
            );
            mission = missionService.findForUpdate(missionId);
            validateLockedRewardCouponPolicyLink(
                mission,
                initiallyReferencedCouponPolicyId
            );
            validateRegionScope(regionAdmin, mission);

            Region region = regionService.findRegionForUpdate(mission.getRegion().getRegionId());
            validatePublicationConditions(region, mission, rewardCouponPolicy);
            validateTargetContents(mission, region);

            Instant publishedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            Mission approvedMission = missionService.approve(mission, publishedAt);
            recordSuccess(requestId, approvedMission, regionAdmin, publishedAt);
            return ApproveRegionAdminMissionResult.from(approvedMission);
        } catch (BusinessException exception) {
            recordFailure(requestId, mission, regionAdmin, exception.getErrorCode());
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(requestId, mission, regionAdmin, ErrorCode.INTERNAL_SERVER_ERROR);
            throw exception;
        }
    }

    private void validateLockedRewardCouponPolicyLink(
        Mission mission,
        Long lockedCouponPolicyId
    ) {
        if (!mission.getRewardCouponPolicy().getCouponPolicyId().equals(lockedCouponPolicyId)) {
            throw new BusinessException(ErrorCode.MISSION_STATE_CONFLICT);
        }
    }

    private void validateRegionScope(
        AuthorizedRegionAdmin regionAdmin,
        Mission mission
    ) {
        if (!regionAdmin.region().getRegionId().equals(mission.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validatePublicationConditions(
        Region region,
        Mission mission,
        CouponPolicy rewardCouponPolicy
    ) {
        if (!region.isPublic() || mission.getStatus() != MissionStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.MISSION_STATE_CONFLICT);
        }
        if (!region.getRegionId().equals(rewardCouponPolicy.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (rewardCouponPolicy.getStatus() != CouponPolicyStatus.PUBLISHED
            || rewardCouponPolicy.getIssuanceType() != CouponIssuanceType.MISSION_REWARD) {
            throw new BusinessException(ErrorCode.MISSION_STATE_CONFLICT);
        }
    }

    private void validateTargetContents(
        Mission mission,
        Region region
    ) {
        if (mission.getConditionType() != MissionConditionType.CONTENT_SET) {
            return;
        }

        List<MissionTargetContent> targetContents = missionTargetContentService
            .findForUpdateOrderByContentId(mission.getMissionId());
        if (targetContents.isEmpty()) {
            throw new BusinessException(ErrorCode.MISSION_STATE_CONFLICT);
        }
        for (MissionTargetContent targetContent : targetContents) {
            Content content = targetContent.getContent();
            if (!region.getRegionId().equals(content.getRegion().getRegionId())
                || content.getDeletedAt() != null
                || content.getStatus() != ContentStatus.PUBLISHED) {
                throw new BusinessException(ErrorCode.MISSION_STATE_CONFLICT);
            }
        }
    }

    private void recordSuccess(
        UUID requestId,
        Mission mission,
        AuthorizedRegionAdmin regionAdmin,
        Instant publishedAt
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            mission.getRegion(),
            AuditEventTargetType.MISSION,
            mission.getMissionId(),
            MissionStatus.PENDING_REVIEW.name(),
            MissionStatus.PUBLISHED.name(),
            AuditEventResult.SUCCESS,
            APPROVAL_REASON_CODE,
            new AuditEventActor(regionAdmin.roleAssignment()),
            publishedAt
        ));
    }

    private void recordFailure(
        UUID requestId,
        Mission mission,
        AuthorizedRegionAdmin regionAdmin,
        ErrorCode errorCode
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            mission.getRegion(),
            AuditEventTargetType.MISSION,
            mission.getMissionId(),
            mission.getStatus().name(),
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            new AuditEventActor(regionAdmin.roleAssignment()),
            clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
    }
}
