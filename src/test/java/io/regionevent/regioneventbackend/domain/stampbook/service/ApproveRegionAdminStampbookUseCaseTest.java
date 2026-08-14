package io.regionevent.regioneventbackend.domain.stampbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService.AuthorizedRegionAdmin;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ApproveRegionAdminStampbookUseCaseTest {

    private static final Long ADMIN_USER_ID = 100L;
    private static final Long REGION_ID = 11L;
    private static final Long STAMPBOOK_ID = 701L;
    private static final Long CONTENT_ID = 101L;
    private static final Long COUPON_POLICY_ID = 501L;
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-14T03:00:00.123456Z");
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String REASON = "대상 콘텐츠와 완료 보상을 확인했습니다.";

    private final RegionAdminAuthorizationService regionAdminAuthorizationService = mock(
        RegionAdminAuthorizationService.class
    );
    private final CouponPolicyService couponPolicyService = mock(CouponPolicyService.class);
    private final StampbookService stampbookService = mock(StampbookService.class);
    private final StampbookContentService stampbookContentService = mock(StampbookContentService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final UserRoleAssignmentService userRoleAssignmentService = mock(
        UserRoleAssignmentService.class
    );
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase = mock(
        RecordFailedAuditEventUseCase.class
    );
    private final ApproveRegionAdminStampbookUseCase useCase = new ApproveRegionAdminStampbookUseCase(
        regionAdminAuthorizationService,
        couponPolicyService,
        stampbookService,
        stampbookContentService,
        contentService,
        userRoleAssignmentService,
        recordAuditEventUseCase,
        recordFailedAuditEventUseCase,
        Clock.fixed(PUBLISHED_AT, ZoneOffset.UTC)
    );

    private Region region;
    private CouponPolicy rewardCouponPolicy;
    private Stampbook initialStampbook;
    private Stampbook lockedStampbook;
    private Stampbook approvedStampbook;
    private AuthorizedRegionAdmin regionAdmin;
    private Content targetContent;

    @BeforeEach
    void setUp() {
        region = mock(Region.class);
        when(region.getRegionId()).thenReturn(REGION_ID);

        AppUser admin = mock(AppUser.class);
        when(admin.getUserId()).thenReturn(ADMIN_USER_ID);
        when(admin.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        UserRoleAssignment adminAssignment = assignment(admin, region, UserRole.REGION_ADMIN);
        regionAdmin = new AuthorizedRegionAdmin(admin, region, adminAssignment);
        when(regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(ADMIN_USER_ID))
            .thenReturn(regionAdmin);

        rewardCouponPolicy = mock(CouponPolicy.class);
        when(rewardCouponPolicy.getCouponPolicyId()).thenReturn(COUPON_POLICY_ID);
        when(rewardCouponPolicy.getRegion()).thenReturn(region);
        when(rewardCouponPolicy.getIssuanceType()).thenReturn(CouponIssuanceType.STAMPBOOK_COMPLETION);
        when(rewardCouponPolicy.getStatus()).thenReturn(CouponPolicyStatus.PUBLISHED);

        initialStampbook = stampbook(rewardCouponPolicy, StampbookStatus.PENDING_REVIEW);
        lockedStampbook = stampbook(rewardCouponPolicy, StampbookStatus.PENDING_REVIEW);
        approvedStampbook = stampbook(rewardCouponPolicy, StampbookStatus.PUBLISHED);
        when(approvedStampbook.getPublishedAt()).thenReturn(PUBLISHED_AT);

        targetContent = targetContent(region);
        when(stampbookService.findStampbook(STAMPBOOK_ID)).thenReturn(initialStampbook);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(rewardCouponPolicy);
        when(stampbookService.findForUpdate(STAMPBOOK_ID)).thenReturn(lockedStampbook);
        when(stampbookContentService.findContentIds(STAMPBOOK_ID)).thenReturn(List.of(CONTENT_ID));
        when(contentService.findStampbookTargetContentsForUpdate(List.of(CONTENT_ID)))
            .thenReturn(List.of(targetContent));
        when(stampbookService.findCurrentDatabaseTime()).thenReturn(PUBLISHED_AT);
        when(stampbookService.approve(lockedStampbook, PUBLISHED_AT)).thenReturn(approvedStampbook);
    }

    @Test
    void approve_검증을통과하면계약된잠금순서로공개하고감사를기록한다() {
        ApproveRegionAdminStampbookResult result = useCase.approve(
            ADMIN_USER_ID,
            new ApproveRegionAdminStampbookUseCase.ApproveRegionAdminStampbookCommand(
                STAMPBOOK_ID,
                REASON
            ),
            REQUEST_ID
        );

        assertThat(result.stampbookId()).isEqualTo(STAMPBOOK_ID);
        assertThat(result.status()).isEqualTo(StampbookStatus.PUBLISHED);
        assertThat(result.publishedAt()).isEqualTo(PUBLISHED_AT);
        InOrder order = inOrder(
            stampbookService,
            couponPolicyService,
            stampbookContentService,
            contentService,
            userRoleAssignmentService,
            recordAuditEventUseCase
        );
        order.verify(stampbookService).findStampbook(STAMPBOOK_ID);
        order.verify(couponPolicyService).findForUpdate(COUPON_POLICY_ID);
        order.verify(stampbookService).findForUpdate(STAMPBOOK_ID);
        order.verify(stampbookContentService).findContentIds(STAMPBOOK_ID);
        order.verify(contentService).findStampbookTargetContentsForUpdate(List.of(CONTENT_ID));
        order.verify(userRoleAssignmentService).findActiveOperatorForUpdate(CONTENT_ID);
        order.verify(stampbookService).findCurrentDatabaseTime();
        order.verify(stampbookService).approve(lockedStampbook, PUBLISHED_AT);
        order.verify(recordAuditEventUseCase).record(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(recordFailedAuditEventUseCase);

        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(commandCaptor.capture());
        AuditEventCommand command = commandCaptor.getValue();
        assertThat(command.targetType()).isEqualTo(AuditEventTargetType.STAMPBOOK);
        assertThat(command.targetId()).isEqualTo(STAMPBOOK_ID);
        assertThat(command.previousState()).isEqualTo(StampbookStatus.PENDING_REVIEW.name());
        assertThat(command.nextState()).isEqualTo(StampbookStatus.PUBLISHED.name());
        assertThat(command.result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(command.reason()).isEqualTo(REASON);
        assertThat(command.occurredAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void approve_공개되지않은완료보상정책이면상태충돌과실패감사를기록한다() {
        when(rewardCouponPolicy.getStatus()).thenReturn(CouponPolicyStatus.DRAFT);

        assertBusinessError(ErrorCode.STAMPBOOK_STATE_CONFLICT);

        assertFailureAudit(ErrorCode.STAMPBOOK_STATE_CONFLICT);
    }

    @Test
    void approve_대상콘텐츠가없으면찾을수없음과감사이력미변경을반환한다() {
        when(contentService.findStampbookTargetContentsForUpdate(List.of(CONTENT_ID)))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertBusinessError(ErrorCode.NOT_FOUND);

        verifyNoInteractions(recordFailedAuditEventUseCase, recordAuditEventUseCase);
    }

    @Test
    void approve_대상콘텐츠운영자가활성승인상태가아니면상태충돌을반환한다() {
        when(userRoleAssignmentService.findActiveOperatorForUpdate(CONTENT_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertBusinessError(ErrorCode.STAMPBOOK_STATE_CONFLICT);

        assertFailureAudit(ErrorCode.STAMPBOOK_STATE_CONFLICT);
    }

    private void assertBusinessError(ErrorCode errorCode) {
        assertThatThrownBy(() -> useCase.approve(
            ADMIN_USER_ID,
            new ApproveRegionAdminStampbookUseCase.ApproveRegionAdminStampbookCommand(
                STAMPBOOK_ID,
                REASON
            ),
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(errorCode)
        );
    }

    private void assertFailureAudit(ErrorCode errorCode) {
        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordFailedAuditEventUseCase).record(commandCaptor.capture());
        AuditEventCommand command = commandCaptor.getValue();
        assertThat(command.targetType()).isEqualTo(AuditEventTargetType.STAMPBOOK);
        assertThat(command.targetId()).isEqualTo(STAMPBOOK_ID);
        assertThat(command.previousState()).isEqualTo(StampbookStatus.PENDING_REVIEW.name());
        assertThat(command.nextState()).isNull();
        assertThat(command.result()).isEqualTo(AuditEventResult.FAILURE);
        assertThat(command.reasonCode()).isEqualTo(errorCode.code());
    }

    private Stampbook stampbook(
        CouponPolicy couponPolicy,
        StampbookStatus status
    ) {
        Stampbook stampbook = mock(Stampbook.class);
        when(stampbook.getStampbookId()).thenReturn(STAMPBOOK_ID);
        when(stampbook.getRegion()).thenReturn(region);
        when(stampbook.getRewardCouponPolicy()).thenReturn(couponPolicy);
        when(stampbook.getStatus()).thenReturn(status);
        return stampbook;
    }

    private Content targetContent(Region contentRegion) {
        Content content = mock(Content.class);
        AppUser operator = mock(AppUser.class);
        when(operator.getUserId()).thenReturn(CONTENT_ID);
        when(content.getRegion()).thenReturn(contentRegion);
        when(content.getOperator()).thenReturn(operator);
        UserRoleAssignment operatorAssignment = assignment(
            operator,
            contentRegion,
            UserRole.OPERATOR
        );
        when(userRoleAssignmentService.findActiveOperatorForUpdate(CONTENT_ID))
            .thenReturn(operatorAssignment);
        return content;
    }

    private UserRoleAssignment assignment(
        AppUser user,
        Region assignedRegion,
        UserRole role
    ) {
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        when(assignment.getRoleAssignmentId()).thenReturn(900L);
        when(assignment.getAppUser()).thenReturn(user);
        when(assignment.getRegion()).thenReturn(assignedRegion);
        when(assignment.getRole()).thenReturn(role);
        return assignment;
    }
}
