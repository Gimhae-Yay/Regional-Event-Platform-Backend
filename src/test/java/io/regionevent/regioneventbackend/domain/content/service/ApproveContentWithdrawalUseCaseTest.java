package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionInvalidationReason;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.payment.service.ExpirePendingPaymentForTerminatedHoldUseCase;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ApproveContentWithdrawalUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 200L;
    private static final Long WITHDRAWAL_REQUEST_ID = 7001L;
    private static final UUID REQUEST_ID = UUID.fromString("4d7c2044-b64f-4bd5-a718-5390198a6819");
    private static final Instant APPROVED_AT = Instant.parse("2026-08-16T06:00:00Z");

    private final ContentWithdrawalRequestService withdrawalRequestService =
        mock(ContentWithdrawalRequestService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final RegionService regionService = mock(RegionService.class);
    private final ContentRevisionInvalidationService revisionInvalidationService =
        mock(ContentRevisionInvalidationService.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final ContentLogService contentLogService = mock(ContentLogService.class);
    private final CapacityHoldService capacityHoldService = mock(CapacityHoldService.class);
    private final ExpirePendingPaymentForTerminatedHoldUseCase expirePaymentUseCase =
        mock(ExpirePendingPaymentForTerminatedHoldUseCase.class);
    private final RegionAdminAuthorizationService authorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final PublicCatalogCacheInvalidator cacheInvalidator = mock(PublicCatalogCacheInvalidator.class);
    private final ApproveContentWithdrawalUseCase useCase = new ApproveContentWithdrawalUseCase(
        withdrawalRequestService,
        contentService,
        regionService,
        revisionInvalidationService,
        contentSessionService,
        contentLogService,
        capacityHoldService,
        expirePaymentUseCase,
        authorizationService,
        recordAuditEventUseCase,
        cacheInvalidator
    );

    private final RegionAdminAuthorizationService.AuthorizedRegionAdmin authorizedAdmin =
        mock(RegionAdminAuthorizationService.AuthorizedRegionAdmin.class);
    private final UserRoleAssignment assignment = mock(UserRoleAssignment.class);
    private final AppUser admin = mock(AppUser.class);
    private final Region region = mock(Region.class);
    private final Content content = mock(Content.class);
    private final ContentWithdrawalRequest withdrawalRequest = mock(ContentWithdrawalRequest.class);

    @BeforeEach
    void setUp() {
        when(authorizationService.requireAuthorizedRegionAdminForUpdate(USER_ID))
            .thenReturn(authorizedAdmin);
        when(withdrawalRequestService.findContentId(WITHDRAWAL_REQUEST_ID)).thenReturn(CONTENT_ID);
        when(contentService.findContentRegionId(CONTENT_ID)).thenReturn(REGION_ID);
        when(regionService.findRegionForUpdate(REGION_ID)).thenReturn(region);
        when(contentService.findForUpdate(CONTENT_ID)).thenReturn(content);
        when(authorizedAdmin.authorize(REGION_ID)).thenReturn(assignment);
        when(withdrawalRequestService.findApprovalTargetForUpdate(WITHDRAWAL_REQUEST_ID))
            .thenReturn(withdrawalRequest);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(assignment.getRoleAssignmentId()).thenReturn(300L);
        when(assignment.getAppUser()).thenReturn(admin);
        when(admin.getUserId()).thenReturn(USER_ID);
        when(admin.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getVersionNo()).thenReturn(4);
        when(withdrawalRequest.getContentWithdrawalRequestId()).thenReturn(WITHDRAWAL_REQUEST_ID);
        when(withdrawalRequest.getRequestReason()).thenReturn("운영 계획 변경");
        when(withdrawalRequest.getReviewedAt()).thenReturn(APPROVED_AT);
    }

    @Test
    void 최초_승인은_잠금_순서대로_연계_상태를_한번만_종결한다() {
        when(withdrawalRequest.getStatus())
            .thenReturn(
                ContentWithdrawalRequestStatus.PENDING,
                ContentWithdrawalRequestStatus.PENDING,
                ContentWithdrawalRequestStatus.APPROVED
            );
        when(content.getStatus()).thenReturn(ContentStatus.PUBLISHED, ContentStatus.WITHDRAWN);
        when(contentService.findCurrentDatabaseTime()).thenReturn(APPROVED_AT);
        when(contentService.withdraw(content, APPROVED_AT)).thenReturn(content);
        when(revisionInvalidationService.invalidateActiveRevisionForContent(any(), any(), any(), any()))
            .thenReturn(Optional.empty());
        CapacityHoldService.TerminatedCapacityHold terminatedCapacityHold =
            new CapacityHoldService.TerminatedCapacityHold(
                500L,
                region,
                2,
                CapacityHoldStatus.INVALIDATED,
                "CONTENT_WITHDRAWN",
                APPROVED_AT
            );
        when(capacityHoldService.invalidateAllActiveHoldsForContent(CONTENT_ID, "CONTENT_WITHDRAWN"))
            .thenReturn(List.of(terminatedCapacityHold));

        ApproveContentWithdrawalResult result = useCase.approve(
            USER_ID,
            WITHDRAWAL_REQUEST_ID,
            REQUEST_ID
        );

        assertThat(result.requestStatus()).isEqualTo(ContentWithdrawalRequestStatus.APPROVED);
        assertThat(result.contentStatus()).isEqualTo(ContentStatus.WITHDRAWN);
        assertThat(result.approvedAt()).isEqualTo(APPROVED_AT);
        InOrder order = inOrder(
            authorizationService,
            withdrawalRequestService,
            contentService,
            regionService,
            authorizedAdmin,
            revisionInvalidationService,
            contentSessionService,
            contentLogService,
            capacityHoldService
        );
        order.verify(authorizationService).requireAuthorizedRegionAdminForUpdate(USER_ID);
        order.verify(withdrawalRequestService).findContentId(WITHDRAWAL_REQUEST_ID);
        order.verify(contentService).findContentRegionId(CONTENT_ID);
        order.verify(regionService).findRegionForUpdate(REGION_ID);
        order.verify(contentService).findForUpdate(CONTENT_ID);
        order.verify(authorizedAdmin).authorize(REGION_ID);
        order.verify(withdrawalRequestService).findApprovalTargetForUpdate(WITHDRAWAL_REQUEST_ID);
        order.verify(contentService).findCurrentDatabaseTime();
        order.verify(withdrawalRequestService).approve(withdrawalRequest, admin, APPROVED_AT);
        order.verify(contentService).withdraw(content, APPROVED_AT);
        order.verify(revisionInvalidationService).invalidateActiveRevisionForContent(
            CONTENT_ID,
            admin,
            APPROVED_AT,
            ContentRevisionInvalidationReason.CONTENT_WITHDRAWN
        );
        order.verify(contentSessionService).lockSuspendTargetsForUpdate(CONTENT_ID);
        order.verify(contentLogService).recordWithdrawn(
            content,
            admin,
            APPROVED_AT,
            "운영 계획 변경"
        );
        order.verify(capacityHoldService).invalidateAllActiveHoldsForContent(
            CONTENT_ID,
            "CONTENT_WITHDRAWN"
        );
        verify(expirePaymentUseCase).expire(
            eq(terminatedCapacityHold),
            eq(REQUEST_ID),
            any()
        );
        verify(cacheInvalidator).invalidateContentAfterCommit(REGION_ID, CONTENT_ID, 4);

        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase, times(2)).record(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues())
            .extracting(AuditEventCommand::targetType, AuditEventCommand::reasonCode)
            .containsExactly(
                tuple(
                    AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST,
                    "CONTENT_WITHDRAWAL_APPROVED"
                ),
                tuple(AuditEventTargetType.CONTENT, "CONTENT_WITHDRAWN")
            );
    }

    @Test
    void 이미_승인된_요청은_저장_결과만_반환하고_부수_효과를_반복하지_않는다() {
        when(withdrawalRequest.getStatus()).thenReturn(ContentWithdrawalRequestStatus.APPROVED);
        when(content.getStatus()).thenReturn(ContentStatus.WITHDRAWN);

        ApproveContentWithdrawalResult result = useCase.approve(
            USER_ID,
            WITHDRAWAL_REQUEST_ID,
            REQUEST_ID
        );

        assertThat(result.requestStatus()).isEqualTo(ContentWithdrawalRequestStatus.APPROVED);
        assertThat(result.contentStatus()).isEqualTo(ContentStatus.WITHDRAWN);
        verify(contentService, never()).findCurrentDatabaseTime();
        verify(withdrawalRequestService, never()).approve(any(), any(), any());
        verify(contentService, never()).withdraw(any(), any());
        verifyNoInteractions(
            revisionInvalidationService,
            contentSessionService,
            contentLogService,
            capacityHoldService,
            expirePaymentUseCase,
            recordAuditEventUseCase,
            cacheInvalidator
        );
    }

    @Test
    void 대기가_아닌_요청은_상태_충돌을_반환한다() {
        when(withdrawalRequest.getStatus()).thenReturn(ContentWithdrawalRequestStatus.REJECTED);

        assertThatThrownBy(() -> useCase.approve(USER_ID, WITHDRAWAL_REQUEST_ID, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
            );

        verify(contentService, never()).withdraw(any(), any());
        verifyNoInteractions(recordAuditEventUseCase, cacheInvalidator);
    }

    @Test
    void 무효화된_요청은_상태_충돌을_반환한다() {
        when(withdrawalRequest.getStatus()).thenReturn(ContentWithdrawalRequestStatus.INVALIDATED);

        assertThatThrownBy(() -> useCase.approve(USER_ID, WITHDRAWAL_REQUEST_ID, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
            );

        verify(contentService, never()).withdraw(any(), any());
        verifyNoInteractions(recordAuditEventUseCase, cacheInvalidator);
    }

    @Test
    void 콘텐츠가_공개_상태가_아니면_상태_충돌을_반환한다() {
        when(withdrawalRequest.getStatus()).thenReturn(ContentWithdrawalRequestStatus.PENDING);
        when(content.getStatus()).thenReturn(ContentStatus.APPROVED);

        assertThatThrownBy(() -> useCase.approve(USER_ID, WITHDRAWAL_REQUEST_ID, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
            );

        verify(contentService, never()).withdraw(any(), any());
        verifyNoInteractions(recordAuditEventUseCase, cacheInvalidator);
    }

    @Test
    void 존재하지_않는_요청은_조회_오류를_그대로_반환한다() {
        when(withdrawalRequestService.findContentId(WITHDRAWAL_REQUEST_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> useCase.approve(USER_ID, WITHDRAWAL_REQUEST_ID, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );

        verify(regionService, never()).findRegionForUpdate(any());
        verifyNoInteractions(recordAuditEventUseCase, cacheInvalidator);
    }

    @Test
    void 담당_지역이_아니면_권한_오류를_그대로_반환한다() {
        when(authorizedAdmin.authorize(REGION_ID)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> useCase.approve(USER_ID, WITHDRAWAL_REQUEST_ID, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(withdrawalRequestService, never()).findApprovalTargetForUpdate(any());
        verifyNoInteractions(recordAuditEventUseCase, cacheInvalidator);
    }
}
