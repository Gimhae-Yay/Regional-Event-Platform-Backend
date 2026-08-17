package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class RejectContentWithdrawalUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 200L;
    private static final Long WITHDRAWAL_REQUEST_ID = 7001L;
    private static final UUID REQUEST_ID = UUID.fromString("e2135398-9ef1-4d76-aaac-797df0cfca12");
    private static final Instant REJECTED_AT = Instant.parse("2026-08-17T01:00:00Z");

    private final ContentWithdrawalRequestService withdrawalRequestService =
        mock(ContentWithdrawalRequestService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final RegionService regionService = mock(RegionService.class);
    private final RegionAdminAuthorizationService authorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RejectContentWithdrawalUseCase useCase = new RejectContentWithdrawalUseCase(
        withdrawalRequestService,
        contentService,
        regionService,
        authorizationService,
        recordAuditEventUseCase
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
        when(withdrawalRequestService.findContentId(WITHDRAWAL_REQUEST_ID)).thenReturn(CONTENT_ID);
        when(authorizationService.requireAuthorizedRegionAdminForUpdate(USER_ID))
            .thenReturn(authorizedAdmin);
        when(contentService.findContentRegionId(CONTENT_ID)).thenReturn(REGION_ID);
        when(regionService.findRegionForUpdate(REGION_ID)).thenReturn(region);
        when(contentService.findForUpdate(CONTENT_ID)).thenReturn(content);
        when(authorizedAdmin.authorize(REGION_ID)).thenReturn(assignment);
        when(withdrawalRequestService.findReviewTargetForUpdate(WITHDRAWAL_REQUEST_ID))
            .thenReturn(withdrawalRequest);
        when(contentService.findCurrentDatabaseTime()).thenReturn(REJECTED_AT);
        when(withdrawalRequestService.reject(
            withdrawalRequest,
            admin,
            REJECTED_AT,
            "운영 근거 부족"
        )).thenReturn(withdrawalRequest);
        when(assignment.getRoleAssignmentId()).thenReturn(300L);
        when(assignment.getAppUser()).thenReturn(admin);
        when(admin.getUserId()).thenReturn(USER_ID);
        when(admin.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(withdrawalRequest.getContentWithdrawalRequestId()).thenReturn(WITHDRAWAL_REQUEST_ID);
        when(withdrawalRequest.getContent()).thenReturn(content);
        when(withdrawalRequest.getRejectionReason()).thenReturn("운영 근거 부족");
        when(withdrawalRequest.getReviewedAt()).thenReturn(REJECTED_AT);
    }

    @Test
    void 최초_반려는_계약된_잠금_순서로_요청과_감사를_한번만_저장한다() {
        when(withdrawalRequest.getStatus()).thenReturn(
            ContentWithdrawalRequestStatus.PENDING,
            ContentWithdrawalRequestStatus.PENDING,
            ContentWithdrawalRequestStatus.REJECTED
        );

        RejectContentWithdrawalResult result = useCase.reject(
            USER_ID,
            WITHDRAWAL_REQUEST_ID,
            "  운영 근거 부족  ",
            REQUEST_ID
        );

        assertThat(result).isEqualTo(new RejectContentWithdrawalResult(
            WITHDRAWAL_REQUEST_ID,
            CONTENT_ID,
            ContentWithdrawalRequestStatus.REJECTED,
            "운영 근거 부족",
            REJECTED_AT
        ));
        InOrder order = inOrder(
            withdrawalRequestService,
            authorizationService,
            contentService,
            regionService,
            authorizedAdmin,
            recordAuditEventUseCase
        );
        order.verify(withdrawalRequestService).findContentId(WITHDRAWAL_REQUEST_ID);
        order.verify(authorizationService).requireAuthorizedRegionAdminForUpdate(USER_ID);
        order.verify(contentService).findContentRegionId(CONTENT_ID);
        order.verify(regionService).findRegionForUpdate(REGION_ID);
        order.verify(contentService).findForUpdate(CONTENT_ID);
        order.verify(authorizedAdmin).authorize(REGION_ID);
        order.verify(withdrawalRequestService).findReviewTargetForUpdate(WITHDRAWAL_REQUEST_ID);
        order.verify(contentService).findCurrentDatabaseTime();
        order.verify(withdrawalRequestService).reject(
            withdrawalRequest,
            admin,
            REJECTED_AT,
            "운영 근거 부족"
        );

        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(auditCaptor.capture());
        AuditEventCommand command = auditCaptor.getValue();
        assertThat(command.requestId()).isEqualTo(REQUEST_ID);
        assertThat(command.region()).isSameAs(region);
        assertThat(command.targetType()).isEqualTo(AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST);
        assertThat(command.targetId()).isEqualTo(WITHDRAWAL_REQUEST_ID);
        assertThat(command.previousState()).isEqualTo(ContentWithdrawalRequestStatus.PENDING.name());
        assertThat(command.nextState()).isEqualTo(ContentWithdrawalRequestStatus.REJECTED.name());
        assertThat(command.result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(command.reasonCode()).isEqualTo("CONTENT_WITHDRAWAL_REJECTED");
        assertThat(command.actor().getAppUser()).isSameAs(admin);
        assertThat(command.occurredAt()).isEqualTo(REJECTED_AT);
    }

    @Test
    void 같은_사유로_반려를_재시도하면_최초_저장_결과만_반환한다() {
        when(withdrawalRequest.getStatus()).thenReturn(ContentWithdrawalRequestStatus.REJECTED);

        RejectContentWithdrawalResult result = useCase.reject(
            USER_ID,
            WITHDRAWAL_REQUEST_ID,
            "  운영 근거 부족  ",
            REQUEST_ID
        );

        assertThat(result.rejectedAt()).isEqualTo(REJECTED_AT);
        verify(contentService, never()).findCurrentDatabaseTime();
        verify(withdrawalRequestService, never()).reject(any(), any(), any(), any());
        verifyNoInteractions(recordAuditEventUseCase);
    }

    @Test
    void 다른_사유로_반려를_재시도하면_상태_충돌이다() {
        when(withdrawalRequest.getStatus()).thenReturn(ContentWithdrawalRequestStatus.REJECTED);

        assertContentStateConflict(() -> useCase.reject(
            USER_ID,
            WITHDRAWAL_REQUEST_ID,
            "다른 사유",
            REQUEST_ID
        ));

        verify(contentService, never()).findCurrentDatabaseTime();
        verifyNoInteractions(recordAuditEventUseCase);
    }

    @ParameterizedTest
    @EnumSource(
        value = ContentWithdrawalRequestStatus.class,
        names = {"APPROVED", "INVALIDATED"}
    )
    void 승인되거나_무효화된_요청은_상태_충돌이다(ContentWithdrawalRequestStatus status) {
        when(withdrawalRequest.getStatus()).thenReturn(status);

        assertContentStateConflict(() -> useCase.reject(
            USER_ID,
            WITHDRAWAL_REQUEST_ID,
            "운영 근거 부족",
            REQUEST_ID
        ));

        verify(contentService, never()).findCurrentDatabaseTime();
        verifyNoInteractions(recordAuditEventUseCase);
    }

    @Test
    void 존재하지_않는_요청은_NOT_FOUND를_반환한다() {
        when(withdrawalRequestService.findContentId(WITHDRAWAL_REQUEST_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> useCase.reject(
            USER_ID,
            WITHDRAWAL_REQUEST_ID,
            "운영 근거 부족",
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
        );

        verifyNoInteractions(authorizationService, regionService, recordAuditEventUseCase);
    }

    @Test
    void 담당_지역이_아니면_FORBIDDEN을_반환한다() {
        when(authorizedAdmin.authorize(REGION_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> useCase.reject(
            USER_ID,
            WITHDRAWAL_REQUEST_ID,
            "운영 근거 부족",
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
        );

        verify(withdrawalRequestService, never()).findReviewTargetForUpdate(any());
        verifyNoInteractions(recordAuditEventUseCase);
    }

    @Test
    void 활성_지역_관리자가_아니면_FORBIDDEN을_반환한다() {
        when(authorizationService.requireAuthorizedRegionAdminForUpdate(USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> useCase.reject(
            USER_ID,
            WITHDRAWAL_REQUEST_ID,
            "운영 근거 부족",
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
        );

        verify(contentService, never()).findContentRegionId(any());
        verifyNoInteractions(regionService, recordAuditEventUseCase);
    }

    private void assertContentStateConflict(ThrowingCall call) {
        assertThatThrownBy(call::invoke)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
            );
    }

    @FunctionalInterface
    private interface ThrowingCall {

        void invoke();
    }
}
