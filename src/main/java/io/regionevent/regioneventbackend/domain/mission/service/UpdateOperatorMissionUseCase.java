package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionUpdateSnapshot;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class UpdateOperatorMissionUseCase {

    private static final ZoneOffset REQUIRED_ENDS_AT_OFFSET = ZoneOffset.ofHours(9);
    private static final String SUCCESS_REASON_CODE = "MISSION_UPDATED";

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final CouponPolicyService couponPolicyService;
    private final MissionService missionService;
    private final ContentService contentService;
    private final MissionTargetContentService missionTargetContentService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Clock clock;

    public UpdateOperatorMissionUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        CouponPolicyService couponPolicyService,
        MissionService missionService,
        ContentService contentService,
        MissionTargetContentService missionTargetContentService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.couponPolicyService = couponPolicyService;
        this.missionService = missionService;
        this.contentService = contentService;
        this.missionTargetContentService = missionTargetContentService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public UpdateOperatorMissionResult update(
        Long userId,
        Long missionId,
        UpdateOperatorMissionCommand command,
        UUID requestId
    ) {
        validateRequestContext(userId, missionId, requestId);
        ValidatedCommand validatedCommand = validateCommand(command);
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperatorForUpdate(userId);
        MissionUpdateSnapshot initialSnapshot = missionService.findUpdateSnapshot(missionId);
        Mission mission = null;
        MissionStatus previousState = initialSnapshot.getStatus();

        try {
            validateRegionScope(operator, initialSnapshot.getRegion());
            Long initiallyReferencedCouponPolicyId = initialSnapshot.getRewardCouponPolicyId();
            List<CouponPolicy> lockedCouponPolicies = lockCouponPolicies(
                initiallyReferencedCouponPolicyId,
                validatedCommand.rewardCouponPolicyId()
            );

            mission = missionService.findByMissionIdForUpdate(missionId);
            previousState = mission.getStatus();
            validateLockedMission(operator, mission, initiallyReferencedCouponPolicyId);

            CouponPolicy requestedRewardCouponPolicy = findLockedCouponPolicy(
                lockedCouponPolicies,
                validatedCommand.rewardCouponPolicyId()
            );
            validateRewardCouponPolicy(requestedRewardCouponPolicy, mission.getRegion().getRegionId());
            List<Content> targetContents = lockTargetContents(validatedCommand, mission);

            Mission updatedMission = missionService.replaceDraftCoreValues(
                mission,
                validatedCommand.conditionType(),
                validatedCommand.requiredVisitCount(),
                requestedRewardCouponPolicy,
                validatedCommand.endsAt()
            );
            missionTargetContentService.replaceAll(updatedMission, targetContents);
            Instant occurredAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            recordSuccess(requestId, updatedMission, operator, occurredAt);
            return UpdateOperatorMissionResult.from(updatedMission);
        } catch (BusinessException exception) {
            Region auditRegion = mission == null ? initialSnapshot.getRegion() : mission.getRegion();
            recordFailure(requestId, auditRegion, missionId, previousState, operator, exception.getErrorCode());
            throw exception;
        } catch (RuntimeException exception) {
            Region auditRegion = mission == null ? initialSnapshot.getRegion() : mission.getRegion();
            recordFailure(
                requestId,
                auditRegion,
                missionId,
                previousState,
                operator,
                ErrorCode.INTERNAL_SERVER_ERROR
            );
            throw exception;
        }
    }

    private void validateRequestContext(
        Long userId,
        Long missionId,
        UUID requestId
    ) {
        if (userId == null || userId <= 0 || missionId == null || missionId <= 0 || requestId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private ValidatedCommand validateCommand(UpdateOperatorMissionCommand command) {
        if (command == null
            || command.conditionType() == null
            || command.rewardCouponPolicyId() == null
            || command.rewardCouponPolicyId() <= 0
            || command.endsAt() == null
            || !command.endsAt().getOffset().equals(REQUIRED_ENDS_AT_OFFSET)
            || !command.endsAt().toInstant().isAfter(clock.instant())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        MissionConditionType conditionType = toConditionType(command.conditionType());
        validateConditionFields(conditionType, command.requiredVisitCount(), command.targetContentIds());
        return new ValidatedCommand(
            conditionType,
            command.requiredVisitCount(),
            normalizeTargetContentIds(command.targetContentIds()),
            command.rewardCouponPolicyId(),
            command.endsAt().toInstant()
        );
    }

    private MissionConditionType toConditionType(String value) {
        try {
            return MissionConditionType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }

    private void validateConditionFields(
        MissionConditionType conditionType,
        Integer requiredVisitCount,
        List<Long> targetContentIds
    ) {
        if (conditionType == MissionConditionType.VISIT_COUNT) {
            if (requiredVisitCount == null
                || requiredVisitCount < 1
                || targetContentIds != null && !targetContentIds.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return;
        }
        if (requiredVisitCount != null || targetContentIds == null || targetContentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private List<Long> normalizeTargetContentIds(List<Long> targetContentIds) {
        if (targetContentIds == null) {
            return List.of();
        }
        if (targetContentIds.stream().anyMatch(contentId -> contentId == null || contentId <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Set<Long> distinctContentIds = new HashSet<>(targetContentIds);
        if (distinctContentIds.size() != targetContentIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return targetContentIds.stream()
            .sorted()
            .toList();
    }

    private List<CouponPolicy> lockCouponPolicies(
        Long currentCouponPolicyId,
        Long requestedCouponPolicyId
    ) {
        return List.of(currentCouponPolicyId, requestedCouponPolicyId).stream()
            .distinct()
            .sorted()
            .map(couponPolicyService::findForUpdate)
            .toList();
    }

    private CouponPolicy findLockedCouponPolicy(
        List<CouponPolicy> lockedCouponPolicies,
        Long couponPolicyId
    ) {
        return lockedCouponPolicies.stream()
            .filter(couponPolicy -> couponPolicy.getCouponPolicyId().equals(couponPolicyId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("locked coupon policy must exist"));
    }

    private void validateLockedMission(
        AuthorizedOperator operator,
        Mission mission,
        Long initiallyReferencedCouponPolicyId
    ) {
        if (!mission.getRewardCouponPolicy().getCouponPolicyId().equals(initiallyReferencedCouponPolicyId)
            || mission.getStatus() != MissionStatus.DRAFT) {
            throw new BusinessException(ErrorCode.MISSION_STATE_CONFLICT);
        }
        validateRegionScope(operator, mission);
    }

    private void validateRegionScope(
        AuthorizedOperator operator,
        Mission mission
    ) {
        validateRegionScope(operator, mission.getRegion());
    }

    private void validateRegionScope(
        AuthorizedOperator operator,
        Region region
    ) {
        if (!operator.region().getRegionId().equals(region.getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateRewardCouponPolicy(
        CouponPolicy rewardCouponPolicy,
        Long regionId
    ) {
        if (!rewardCouponPolicy.getRegion().getRegionId().equals(regionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (rewardCouponPolicy.getIssuanceType() != CouponIssuanceType.MISSION_REWARD
            || (rewardCouponPolicy.getStatus() != CouponPolicyStatus.DRAFT
                && rewardCouponPolicy.getStatus() != CouponPolicyStatus.PUBLISHED)) {
            throw new BusinessException(ErrorCode.MISSION_STATE_CONFLICT);
        }
    }

    private List<Content> lockTargetContents(
        ValidatedCommand command,
        Mission mission
    ) {
        if (command.conditionType() != MissionConditionType.CONTENT_SET) {
            return List.of();
        }
        return contentService.findMissionTargetContentsForUpdate(
            command.targetContentIds(),
            mission.getRegion().getRegionId()
        );
    }

    private void recordSuccess(
        UUID requestId,
        Mission mission,
        AuthorizedOperator operator,
        Instant occurredAt
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            mission.getRegion(),
            AuditEventTargetType.MISSION,
            mission.getMissionId(),
            MissionStatus.DRAFT.name(),
            MissionStatus.DRAFT.name(),
            AuditEventResult.SUCCESS,
            SUCCESS_REASON_CODE,
            new AuditEventActor(operator.roleAssignment()),
            occurredAt
        ));
    }

    private void recordFailure(
        UUID requestId,
        Region region,
        Long missionId,
        MissionStatus previousState,
        AuthorizedOperator operator,
        ErrorCode errorCode
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            AuditEventTargetType.MISSION,
            missionId,
            previousState.name(),
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            new AuditEventActor(operator.roleAssignment()),
            clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
    }

    public record UpdateOperatorMissionCommand(
        String conditionType,
        Integer requiredVisitCount,
        List<Long> targetContentIds,
        Long rewardCouponPolicyId,
        OffsetDateTime endsAt
    ) {
    }

    private record ValidatedCommand(
        MissionConditionType conditionType,
        Integer requiredVisitCount,
        List<Long> targetContentIds,
        Long rewardCouponPolicyId,
        Instant endsAt
    ) {
    }
}
