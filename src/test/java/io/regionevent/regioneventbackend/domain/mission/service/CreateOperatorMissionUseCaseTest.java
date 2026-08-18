package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
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
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class CreateOperatorMissionUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000628");

    private OperatorAuthorizationService operatorAuthorizationService;
    private ContentService contentService;
    private CouponPolicyService couponPolicyService;
    private MissionService missionService;
    private RecordAuditEventUseCase recordAuditEventUseCase;
    private RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private SimpleMeterRegistry meterRegistry;
    private CreateOperatorMissionUseCase useCase;

    @BeforeEach
    void setUp() {
        operatorAuthorizationService = mock(OperatorAuthorizationService.class);
        contentService = mock(ContentService.class);
        couponPolicyService = mock(CouponPolicyService.class);
        missionService = mock(MissionService.class);
        recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
        recordFailedAuditEventUseCase = mock(RecordFailedAuditEventUseCase.class);
        meterRegistry = new SimpleMeterRegistry();
        useCase = new CreateOperatorMissionUseCase(
            operatorAuthorizationService,
            contentService,
            couponPolicyService,
            missionService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase,
            meterRegistry,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void create_withVisitCountCondition_createsDraftMissionAndSuccessAudit() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        CouponPolicy rewardCouponPolicy = rewardCouponPolicy(11L, CouponIssuanceType.MISSION_REWARD, CouponPolicyStatus.DRAFT);
        Mission mission = mission(701L, MissionStatus.DRAFT);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(501L)).thenReturn(rewardCouponPolicy);
        when(missionService.create(
            "김해 미션",
            operator.region(),
            MissionConditionType.VISIT_COUNT,
            3,
            rewardCouponPolicy,
            Instant.parse("2026-09-30T14:59:59Z")
        )).thenReturn(mission);
        when(missionService.save(mission)).thenReturn(mission);

        CreateOperatorMissionResult result = useCase.create(
            100L,
            command("VISIT_COUNT", 3, List.of(), 501L, "2026-09-30T23:59:59+09:00"),
            REQUEST_ID
        );

        assertThat(result.missionId()).isEqualTo(701L);
        assertThat(result.status()).isEqualTo(MissionStatus.DRAFT);
        verifyNoInteractions(contentService);
        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue()).satisfies(audit -> {
            assertThat(audit.requestId()).isEqualTo(REQUEST_ID);
            assertThat(audit.targetType()).isEqualTo(AuditEventTargetType.MISSION);
            assertThat(audit.targetId()).isEqualTo(701L);
            assertThat(audit.previousState()).isNull();
            assertThat(audit.nextState()).isEqualTo(MissionStatus.DRAFT.name());
            assertThat(audit.result()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(audit.reasonCode()).isEqualTo("MISSION_CREATED");
            assertThat(audit.occurredAt()).isEqualTo(NOW);
        });
        verifyNoInteractions(recordFailedAuditEventUseCase);
        assertThat(missingTitleCounter()).isZero();
    }

    @Test
    void create_withMissingTitle_incrementsCompatibilityCounterWithoutHighCardinalityTags() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        CouponPolicy rewardCouponPolicy = rewardCouponPolicy(
            11L,
            CouponIssuanceType.MISSION_REWARD,
            CouponPolicyStatus.DRAFT
        );
        Mission mission = mission(701L, MissionStatus.DRAFT);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(501L)).thenReturn(rewardCouponPolicy);
        when(missionService.create(
            null,
            operator.region(),
            MissionConditionType.VISIT_COUNT,
            3,
            rewardCouponPolicy,
            Instant.parse("2026-09-30T14:59:59Z")
        )).thenReturn(mission);
        when(missionService.save(mission)).thenReturn(mission);

        useCase.create(
            100L,
            command(null, "VISIT_COUNT", 3, List.of(), 501L, "2026-09-30T23:59:59+09:00"),
            REQUEST_ID
        );

        assertThat(missingTitleCounter()).isEqualTo(1);
        assertThat(meterRegistry.get("mission.title.compatibility.missing")
            .tag("operation", "create")
            .counter()
            .getId()
            .getTags())
            .extracting(tag -> tag.getKey() + "=" + tag.getValue())
            .containsExactly("operation=create");
    }

    @Test
    void create_withBlankOrOverlongTitle_doesNotIncrementMissingTitleCounter() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        CouponPolicy rewardCouponPolicy = rewardCouponPolicy(
            11L,
            CouponIssuanceType.MISSION_REWARD,
            CouponPolicyStatus.DRAFT
        );
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(501L)).thenReturn(rewardCouponPolicy);
        for (String invalidTitle : List.of("   ", "가".repeat(256))) {
            when(missionService.create(
                invalidTitle,
                operator.region(),
                MissionConditionType.VISIT_COUNT,
                3,
                rewardCouponPolicy,
                Instant.parse("2026-09-30T14:59:59Z")
            )).thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));

            assertThatThrownBy(() -> useCase.create(
                100L,
                command(
                    invalidTitle,
                    "VISIT_COUNT",
                    3,
                    List.of(),
                    501L,
                    "2026-09-30T23:59:59+09:00"
                ),
                REQUEST_ID
            )).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        assertThat(missingTitleCounter()).isZero();
    }

    @Test
    void create_withContentSetCondition_locksTargetContentsInAscendingOrder() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        CouponPolicy rewardCouponPolicy = rewardCouponPolicy(11L, CouponIssuanceType.MISSION_REWARD, CouponPolicyStatus.PUBLISHED);
        Content firstContent = mock(Content.class);
        Content secondContent = mock(Content.class);
        Mission mission = mission(701L, MissionStatus.DRAFT);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(501L)).thenReturn(rewardCouponPolicy);
        when(contentService.findMissionTargetContentsForUpdate(List.of(101L, 102L), 11L))
            .thenReturn(List.of(firstContent, secondContent));
        when(missionService.create(
            "김해 미션",
            operator.region(),
            MissionConditionType.CONTENT_SET,
            null,
            rewardCouponPolicy,
            Instant.parse("2026-09-30T14:59:59Z")
        )).thenReturn(mission);
        when(missionService.save(mission)).thenReturn(mission);

        useCase.create(
            100L,
            command("CONTENT_SET", null, List.of(102L, 101L), 501L, "2026-09-30T23:59:59+09:00"),
            REQUEST_ID
        );

        verify(contentService).findMissionTargetContentsForUpdate(List.of(101L, 102L), 11L);
        verify(mission).addTargetContent(firstContent);
        verify(mission).addTargetContent(secondContent);
    }

    @Test
    void create_withInvalidConditionFields_throwsInvalidInputBeforeLockingResources() {
        assertThatThrownBy(() -> useCase.create(
            100L,
            command("CONTENT_SET", 3, List.of(101L), 501L, "2026-09-30T23:59:59+09:00"),
            REQUEST_ID
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(operatorAuthorizationService, couponPolicyService, contentService, missionService);
    }

    @Test
    void create_withPastOrNonSeoulEndsAt_throwsInvalidInput() {
        assertInvalidInput(command("VISIT_COUNT", 3, List.of(), 501L, "2026-08-09T08:59:59+09:00"));
        assertInvalidInput(command("VISIT_COUNT", 3, List.of(), 501L, "2026-09-30T23:59:59Z"));
    }

    @Test
    void create_withInvalidRewardCouponPolicy_throwsMissionStateConflict() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        CouponPolicy rewardCouponPolicy = rewardCouponPolicy(11L, CouponIssuanceType.VISIT, CouponPolicyStatus.DRAFT);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(501L)).thenReturn(rewardCouponPolicy);

        assertThatThrownBy(() -> useCase.create(
            100L,
            command("VISIT_COUNT", 3, List.of(), 501L, "2026-09-30T23:59:59+09:00"),
            REQUEST_ID
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.MISSION_STATE_CONFLICT);

        verifyNoInteractions(contentService, missionService, recordAuditEventUseCase);
        assertFailureAudit(ErrorCode.MISSION_STATE_CONFLICT, operator.region());
    }

    @Test
    void create_withEndedRewardCouponPolicy_throwsMissionStateConflict() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        CouponPolicy rewardCouponPolicy = rewardCouponPolicy(
            11L,
            CouponIssuanceType.MISSION_REWARD,
            CouponPolicyStatus.ENDED
        );
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(501L)).thenReturn(rewardCouponPolicy);

        assertThatThrownBy(() -> useCase.create(
            100L,
            command("VISIT_COUNT", 3, List.of(), 501L, "2026-09-30T23:59:59+09:00"),
            REQUEST_ID
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.MISSION_STATE_CONFLICT);

        verifyNoInteractions(contentService, missionService, recordAuditEventUseCase);
        assertFailureAudit(ErrorCode.MISSION_STATE_CONFLICT, operator.region());
    }

    @Test
    void create_withMissingRewardCouponPolicy_propagatesNotFound() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(501L)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> useCase.create(
            100L,
            command("VISIT_COUNT", 3, List.of(), 501L, "2026-09-30T23:59:59+09:00"),
            REQUEST_ID
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.NOT_FOUND);

        verifyNoInteractions(contentService, missionService, recordAuditEventUseCase);
        assertFailureAudit(ErrorCode.NOT_FOUND, operator.region());
    }

    @Test
    void create_withDifferentRewardCouponPolicyRegion_throwsForbidden() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        CouponPolicy rewardCouponPolicy = rewardCouponPolicy(12L, CouponIssuanceType.MISSION_REWARD, CouponPolicyStatus.DRAFT);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(501L)).thenReturn(rewardCouponPolicy);

        assertThatThrownBy(() -> useCase.create(
            100L,
            command("VISIT_COUNT", 3, List.of(), 501L, "2026-09-30T23:59:59+09:00"),
            REQUEST_ID
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.FORBIDDEN);

        assertFailureAudit(ErrorCode.FORBIDDEN, operator.region());
    }

    @Test
    void create_withoutAuthorizedOperator_propagatesForbiddenBeforeLockingRewardPolicy() {
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> useCase.create(
            100L,
            command("VISIT_COUNT", 3, List.of(), 501L, "2026-09-30T23:59:59+09:00"),
            REQUEST_ID
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(
            couponPolicyService,
            contentService,
            missionService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase
        );
    }

    @Test
    void create_whenProcessingExceptionOccurs_recordsInternalServerErrorFailureAudit() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        CouponPolicy rewardCouponPolicy = rewardCouponPolicy(
            11L,
            CouponIssuanceType.MISSION_REWARD,
            CouponPolicyStatus.DRAFT
        );
        Mission mission = mission(701L, MissionStatus.DRAFT);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(501L)).thenReturn(rewardCouponPolicy);
        when(missionService.create(
            "김해 미션",
            operator.region(),
            MissionConditionType.VISIT_COUNT,
            3,
            rewardCouponPolicy,
            Instant.parse("2026-09-30T14:59:59Z")
        )).thenReturn(mission);
        when(missionService.save(mission)).thenThrow(new IllegalStateException("storage failure"));

        assertThatThrownBy(() -> useCase.create(
            100L,
            command("VISIT_COUNT", 3, List.of(), 501L, "2026-09-30T23:59:59+09:00"),
            REQUEST_ID
        )).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(recordAuditEventUseCase);
        assertFailureAudit(ErrorCode.INTERNAL_SERVER_ERROR, operator.region());
    }

    private void assertInvalidInput(CreateOperatorMissionUseCase.CreateOperatorMissionCommand command) {
        assertThatThrownBy(() -> useCase.create(100L, command, REQUEST_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT);
        verifyNoInteractions(recordFailedAuditEventUseCase);
    }

    private void assertFailureAudit(
        ErrorCode errorCode,
        Region region
    ) {
        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordFailedAuditEventUseCase).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue()).satisfies(audit -> {
            assertThat(audit.requestId()).isEqualTo(REQUEST_ID);
            assertThat(audit.region()).isSameAs(region);
            assertThat(audit.targetType()).isEqualTo(AuditEventTargetType.MISSION);
            assertThat(audit.targetId()).isNull();
            assertThat(audit.previousState()).isNull();
            assertThat(audit.nextState()).isNull();
            assertThat(audit.result()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(audit.reasonCode()).isEqualTo(errorCode.code());
            assertThat(audit.actor().getRole()).isEqualTo(UserRole.OPERATOR);
            assertThat(audit.occurredAt()).isEqualTo(NOW);
        });
    }

    private CreateOperatorMissionUseCase.CreateOperatorMissionCommand command(
        String conditionType,
        Integer requiredVisitCount,
        List<Long> targetContentIds,
        Long rewardCouponPolicyId,
        String endsAt
    ) {
        return command(
            "김해 미션",
            conditionType,
            requiredVisitCount,
            targetContentIds,
            rewardCouponPolicyId,
            endsAt
        );
    }

    private CreateOperatorMissionUseCase.CreateOperatorMissionCommand command(
        String title,
        String conditionType,
        Integer requiredVisitCount,
        List<Long> targetContentIds,
        Long rewardCouponPolicyId,
        String endsAt
    ) {
        return new CreateOperatorMissionUseCase.CreateOperatorMissionCommand(
            title,
            conditionType,
            requiredVisitCount,
            targetContentIds,
            rewardCouponPolicyId,
            OffsetDateTime.parse(endsAt)
        );
    }

    private double missingTitleCounter() {
        return meterRegistry.get("mission.title.compatibility.missing")
            .tag("operation", "create")
            .counter()
            .count();
    }

    private AuthorizedOperator operator(
        Long userId,
        Long regionId,
        Long roleAssignmentId
    ) {
        AppUser user = mock(AppUser.class);
        Region region = mock(Region.class);
        UserRoleAssignment roleAssignment = mock(UserRoleAssignment.class);
        when(user.getUserId()).thenReturn(userId);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(region.getRegionId()).thenReturn(regionId);
        when(roleAssignment.getRoleAssignmentId()).thenReturn(roleAssignmentId);
        when(roleAssignment.getAppUser()).thenReturn(user);
        when(roleAssignment.getRole()).thenReturn(UserRole.OPERATOR);
        return new AuthorizedOperator(user, region, roleAssignment);
    }

    private CouponPolicy rewardCouponPolicy(
        Long regionId,
        CouponIssuanceType issuanceType,
        CouponPolicyStatus status
    ) {
        Region region = mock(Region.class);
        CouponPolicy couponPolicy = mock(CouponPolicy.class);
        when(region.getRegionId()).thenReturn(regionId);
        when(couponPolicy.getRegion()).thenReturn(region);
        when(couponPolicy.getIssuanceType()).thenReturn(issuanceType);
        when(couponPolicy.getStatus()).thenReturn(status);
        return couponPolicy;
    }

    private Mission mission(
        Long missionId,
        MissionStatus status
    ) {
        Mission mission = mock(Mission.class);
        when(mission.getMissionId()).thenReturn(missionId);
        when(mission.getStatus()).thenReturn(status);
        return mission;
    }
}
