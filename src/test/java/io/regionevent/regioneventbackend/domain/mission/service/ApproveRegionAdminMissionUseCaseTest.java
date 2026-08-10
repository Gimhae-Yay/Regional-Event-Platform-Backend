package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
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
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService.AuthorizedRegionAdmin;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ApproveRegionAdminMissionUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 11L;
    private static final Long MISSION_ID = 701L;
    private static final Long COUPON_POLICY_ID = 501L;
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-10T04:30:00.123456Z");
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final RegionAdminAuthorizationService authorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final CouponPolicyService couponPolicyService = mock(CouponPolicyService.class);
    private final MissionService missionService = mock(MissionService.class);
    private final RegionService regionService = mock(RegionService.class);
    private final MissionTargetContentService targetContentService =
        mock(MissionTargetContentService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase =
        mock(RecordAuditEventUseCase.class);
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase =
        mock(RecordFailedAuditEventUseCase.class);
    private final Clock clock = Clock.fixed(PUBLISHED_AT, ZoneOffset.UTC);
    private final ApproveRegionAdminMissionUseCase useCase = new ApproveRegionAdminMissionUseCase(
        authorizationService,
        couponPolicyService,
        missionService,
        regionService,
        targetContentService,
        recordAuditEventUseCase,
        recordFailedAuditEventUseCase,
        clock
    );

    private Region region;
    private CouponPolicy rewardCouponPolicy;
    private Mission initialMission;
    private Mission lockedMission;
    private Mission publishedMission;
    private AuthorizedRegionAdmin regionAdmin;

    @BeforeEach
    void setUp() {
        region = mock(Region.class);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(region.isPublic()).thenReturn(true);

        AppUser user = mock(AppUser.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        when(assignment.getRoleAssignmentId()).thenReturn(900L);
        when(assignment.getAppUser()).thenReturn(user);
        when(assignment.getRegion()).thenReturn(region);
        when(assignment.getRole()).thenReturn(UserRole.REGION_ADMIN);
        regionAdmin = new AuthorizedRegionAdmin(user, region, assignment);
        when(authorizationService.requireAuthorizedRegionAdmin(USER_ID)).thenReturn(regionAdmin);

        rewardCouponPolicy = mock(CouponPolicy.class);
        when(rewardCouponPolicy.getCouponPolicyId()).thenReturn(COUPON_POLICY_ID);
        when(rewardCouponPolicy.getRegion()).thenReturn(region);
        when(rewardCouponPolicy.getStatus()).thenReturn(CouponPolicyStatus.PUBLISHED);
        when(rewardCouponPolicy.getIssuanceType()).thenReturn(CouponIssuanceType.MISSION_REWARD);

        initialMission = mission(MissionConditionType.VISIT_COUNT, rewardCouponPolicy);
        lockedMission = mission(MissionConditionType.VISIT_COUNT, rewardCouponPolicy);
        publishedMission = mock(Mission.class);
        when(publishedMission.getMissionId()).thenReturn(MISSION_ID);
        when(publishedMission.getRegion()).thenReturn(region);
        when(publishedMission.getStatus()).thenReturn(MissionStatus.PUBLISHED);
        when(publishedMission.getPublishedAt()).thenReturn(PUBLISHED_AT);

        when(missionService.findMission(MISSION_ID)).thenReturn(initialMission);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(rewardCouponPolicy);
        when(missionService.findForUpdate(MISSION_ID)).thenReturn(lockedMission);
        when(regionService.findRegionForUpdate(REGION_ID)).thenReturn(region);
        when(missionService.approve(lockedMission, PUBLISHED_AT)).thenReturn(publishedMission);
    }

    @Test
    void approve_visitCountMission_locksInContractOrderAndRecordsSuccessAudit() {
        ApproveRegionAdminMissionResult result = useCase.approve(USER_ID, MISSION_ID, REQUEST_ID);

        assertThat(result.missionId()).isEqualTo(MISSION_ID);
        assertThat(result.status()).isEqualTo(MissionStatus.PUBLISHED);
        assertThat(result.publishedAt()).isEqualTo(PUBLISHED_AT);
        InOrder order = inOrder(
            missionService,
            couponPolicyService,
            regionService,
            recordAuditEventUseCase
        );
        order.verify(missionService).findMission(MISSION_ID);
        order.verify(couponPolicyService).findForUpdate(COUPON_POLICY_ID);
        order.verify(missionService).findForUpdate(MISSION_ID);
        order.verify(regionService).findRegionForUpdate(REGION_ID);
        order.verify(missionService).approve(lockedMission, PUBLISHED_AT);
        order.verify(recordAuditEventUseCase).record(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(targetContentService, recordFailedAuditEventUseCase);

        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(commandCaptor.capture());
        AuditEventCommand command = commandCaptor.getValue();
        assertThat(command.requestId()).isEqualTo(REQUEST_ID);
        assertThat(command.targetType()).isEqualTo(AuditEventTargetType.MISSION);
        assertThat(command.targetId()).isEqualTo(MISSION_ID);
        assertThat(command.previousState()).isEqualTo("PENDING_REVIEW");
        assertThat(command.nextState()).isEqualTo("PUBLISHED");
        assertThat(command.result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(command.reasonCode()).isEqualTo("MISSION_APPROVED");
        assertThat(command.occurredAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void approve_contentSetMission_locksAndValidatesPublishedTargets() {
        when(lockedMission.getConditionType()).thenReturn(MissionConditionType.CONTENT_SET);
        MissionTargetContent targetContent = publishedTargetContent();
        when(targetContentService.findForUpdateOrderByContentId(MISSION_ID))
            .thenReturn(List.of(targetContent));

        useCase.approve(USER_ID, MISSION_ID, REQUEST_ID);

        verify(targetContentService).findForUpdateOrderByContentId(MISSION_ID);
        verify(missionService).approve(lockedMission, PUBLISHED_AT);
    }

    @Test
    void approve_whenLockedMissionReferencesDifferentPolicy_recordsConflictFailure() {
        CouponPolicy changedPolicy = mock(CouponPolicy.class);
        when(changedPolicy.getCouponPolicyId()).thenReturn(999L);
        when(lockedMission.getRewardCouponPolicy()).thenReturn(changedPolicy);

        assertBusinessError(ErrorCode.MISSION_STATE_CONFLICT);

        verifyNoInteractions(regionService, targetContentService, recordAuditEventUseCase);
        assertFailureAudit(ErrorCode.MISSION_STATE_CONFLICT);
    }

    @Test
    void approve_whenRewardPolicyBelongsToOtherRegion_recordsForbiddenFailure() {
        Region otherRegion = mock(Region.class);
        when(otherRegion.getRegionId()).thenReturn(22L);
        when(rewardCouponPolicy.getRegion()).thenReturn(otherRegion);

        assertBusinessError(ErrorCode.FORBIDDEN);

        assertFailureAudit(ErrorCode.FORBIDDEN);
    }

    @Test
    void approve_whenTargetContentIsNotPublished_recordsConflictFailure() {
        when(lockedMission.getConditionType()).thenReturn(MissionConditionType.CONTENT_SET);
        MissionTargetContent targetContent = publishedTargetContent();
        when(targetContent.getContent().getStatus()).thenReturn(ContentStatus.APPROVED);
        when(targetContentService.findForUpdateOrderByContentId(MISSION_ID))
            .thenReturn(List.of(targetContent));

        assertBusinessError(ErrorCode.MISSION_STATE_CONFLICT);

        verify(missionService, never()).approve(lockedMission, PUBLISHED_AT);
        assertFailureAudit(ErrorCode.MISSION_STATE_CONFLICT);
    }

    @Test
    void approve_whenPublicationTimeReachedMissionEnd_recordsInvalidInputFailure() {
        when(missionService.approve(lockedMission, PUBLISHED_AT))
            .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));

        assertBusinessError(ErrorCode.INVALID_INPUT);

        assertFailureAudit(ErrorCode.INVALID_INPUT);
    }

    @Test
    void approve_whenMissionCannotBeIdentified_doesNotRecordFailureAudit() {
        when(missionService.findMission(MISSION_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> useCase.approve(USER_ID, MISSION_ID, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );
        verifyNoInteractions(recordFailedAuditEventUseCase, recordAuditEventUseCase);
    }

    private Mission mission(
        MissionConditionType conditionType,
        CouponPolicy couponPolicy
    ) {
        Mission mission = mock(Mission.class);
        when(mission.getMissionId()).thenReturn(MISSION_ID);
        when(mission.getRegion()).thenReturn(region);
        when(mission.getRewardCouponPolicy()).thenReturn(couponPolicy);
        when(mission.getStatus()).thenReturn(MissionStatus.PENDING_REVIEW);
        when(mission.getConditionType()).thenReturn(conditionType);
        return mission;
    }

    private MissionTargetContent publishedTargetContent() {
        Content content = mock(Content.class);
        when(content.getRegion()).thenReturn(region);
        when(content.getStatus()).thenReturn(ContentStatus.PUBLISHED);
        when(content.getDeletedAt()).thenReturn(null);
        MissionTargetContent targetContent = mock(MissionTargetContent.class);
        when(targetContent.getContent()).thenReturn(content);
        return targetContent;
    }

    private void assertBusinessError(ErrorCode errorCode) {
        assertThatThrownBy(() -> useCase.approve(USER_ID, MISSION_ID, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(errorCode)
            );
    }

    private void assertFailureAudit(ErrorCode errorCode) {
        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordFailedAuditEventUseCase).record(commandCaptor.capture());
        AuditEventCommand command = commandCaptor.getValue();
        assertThat(command.requestId()).isEqualTo(REQUEST_ID);
        assertThat(command.targetType()).isEqualTo(AuditEventTargetType.MISSION);
        assertThat(command.targetId()).isEqualTo(MISSION_ID);
        assertThat(command.previousState()).isEqualTo("PENDING_REVIEW");
        assertThat(command.nextState()).isNull();
        assertThat(command.result()).isEqualTo(AuditEventResult.FAILURE);
        assertThat(command.reasonCode()).isEqualTo(errorCode.code());
    }
}
