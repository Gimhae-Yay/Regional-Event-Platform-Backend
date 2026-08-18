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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

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
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreateOperatorMissionUseCase {

    private static final ZoneOffset REQUIRED_ENDS_AT_OFFSET = ZoneOffset.ofHours(9);
    private static final String SUCCESS_REASON_CODE = "MISSION_CREATED";
    private static final String MISSING_TITLE_COUNTER_NAME = "mission.title.compatibility.missing";

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final ContentService contentService;
    private final CouponPolicyService couponPolicyService;
    private final MissionService missionService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Counter missingTitleCounter;
    private final Clock clock;

    public CreateOperatorMissionUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        ContentService contentService,
        CouponPolicyService couponPolicyService,
        MissionService missionService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        MeterRegistry meterRegistry,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.contentService = contentService;
        this.couponPolicyService = couponPolicyService;
        this.missionService = missionService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.missingTitleCounter = meterRegistry.counter(
            MISSING_TITLE_COUNTER_NAME,
            "operation",
            "create"
        );
        this.clock = clock;
    }

    @Transactional
    public CreateOperatorMissionResult create(
        Long userId,
        CreateOperatorMissionCommand command,
        UUID requestId
    ) {
        ValidatedCommand validatedCommand = validateCommand(command);
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperatorForUpdate(userId);
        if (validatedCommand.title() == null) {
            missingTitleCounter.increment();
        }

        try {
            CouponPolicy rewardCouponPolicy = couponPolicyService.findForUpdate(
                validatedCommand.rewardCouponPolicyId()
            );
            validateRewardCouponPolicy(rewardCouponPolicy, operator.region().getRegionId());

            List<Content> targetContents = List.of();
            if (validatedCommand.conditionType() == MissionConditionType.CONTENT_SET) {
                targetContents = contentService.findMissionTargetContentsForUpdate(
                    validatedCommand.targetContentIds(),
                    operator.region().getRegionId()
                );
            }

            Mission mission = missionService.create(
                validatedCommand.title(),
                operator.region(),
                validatedCommand.conditionType(),
                validatedCommand.requiredVisitCount(),
                rewardCouponPolicy,
                validatedCommand.endsAt()
            );
            for (Content targetContent : targetContents) {
                mission.addTargetContent(targetContent);
            }
            mission = missionService.save(mission);

            recordAuditEventUseCase.record(new AuditEventCommand(
                requestId,
                operator.region(),
                AuditEventTargetType.MISSION,
                mission.getMissionId(),
                null,
                MissionStatus.DRAFT.name(),
                AuditEventResult.SUCCESS,
                SUCCESS_REASON_CODE,
                new AuditEventActor(operator.roleAssignment()),
                clock.instant()
            ));
            return CreateOperatorMissionResult.from(mission);
        } catch (BusinessException exception) {
            recordFailure(requestId, operator, exception.getErrorCode());
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(requestId, operator, ErrorCode.INTERNAL_SERVER_ERROR);
            throw exception;
        }
    }

    private ValidatedCommand validateCommand(CreateOperatorMissionCommand command) {
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
            command.title(),
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

    private void validateRewardCouponPolicy(
        CouponPolicy rewardCouponPolicy,
        Long regionId
    ) {
        if (!rewardCouponPolicy.getRegion().getRegionId().equals(regionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (rewardCouponPolicy.getIssuanceType() != CouponIssuanceType.MISSION_REWARD
            || rewardCouponPolicy.getStatus() == CouponPolicyStatus.ENDED) {
            throw new BusinessException(ErrorCode.MISSION_STATE_CONFLICT);
        }
    }

    private void recordFailure(
        UUID requestId,
        AuthorizedOperator operator,
        ErrorCode errorCode
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            operator.region(),
            AuditEventTargetType.MISSION,
            null,
            null,
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            new AuditEventActor(operator.roleAssignment()),
            clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
    }

    public record CreateOperatorMissionCommand(
        String title,
        String conditionType,
        Integer requiredVisitCount,
        List<Long> targetContentIds,
        Long rewardCouponPolicyId,
        OffsetDateTime endsAt
    ) {
    }

    private record ValidatedCommand(
        String title,
        MissionConditionType conditionType,
        Integer requiredVisitCount,
        List<Long> targetContentIds,
        Long rewardCouponPolicyId,
        Instant endsAt
    ) {
    }
}
