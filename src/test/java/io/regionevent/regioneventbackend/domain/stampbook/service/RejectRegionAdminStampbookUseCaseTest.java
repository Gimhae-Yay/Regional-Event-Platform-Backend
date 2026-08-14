package io.regionevent.regioneventbackend.domain.stampbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
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
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class RejectRegionAdminStampbookUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 11L;
    private static final Long STAMPBOOK_ID = 701L;
    private static final Long COUPON_POLICY_ID = 301L;
    private static final Long CONTENT_ID = 901L;
    private static final Instant REJECTED_AT = Instant.parse("2026-08-14T03:05:00.123456Z");
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final CouponPolicyService couponPolicyService = mock(CouponPolicyService.class);
    private final StampbookService stampbookService = mock(StampbookService.class);
    private final StampbookContentService stampbookContentService = mock(StampbookContentService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase = mock(
        RecordFailedAuditEventUseCase.class
    );
    private final RejectRegionAdminStampbookUseCase useCase = new RejectRegionAdminStampbookUseCase(
        regionAdminAuthorizationService,
        couponPolicyService,
        stampbookService,
        stampbookContentService,
        contentService,
        recordAuditEventUseCase,
        recordFailedAuditEventUseCase
    );

    private Region region;
    private Stampbook initialStampbook;
    private Stampbook lockedStampbook;
    private Stampbook rejectedStampbook;

    @BeforeEach
    void setUp() {
        region = mock(Region.class);
        when(region.getRegionId()).thenReturn(REGION_ID);
        AuthorizedRegionAdmin regionAdmin = authorizedRegionAdmin();
        when(regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(USER_ID))
            .thenReturn(regionAdmin);

        initialStampbook = stampbook(StampbookStatus.PENDING_REVIEW);
        lockedStampbook = stampbook(StampbookStatus.PENDING_REVIEW);
        rejectedStampbook = stampbook(StampbookStatus.DRAFT);
        when(stampbookService.findStampbook(STAMPBOOK_ID)).thenReturn(initialStampbook);
        when(stampbookService.findForUpdate(STAMPBOOK_ID)).thenReturn(lockedStampbook);
        when(stampbookContentService.findContentIds(STAMPBOOK_ID)).thenReturn(List.of(CONTENT_ID));
        when(stampbookService.findCurrentDatabaseTime()).thenReturn(REJECTED_AT);
        when(stampbookService.reject(lockedStampbook)).thenReturn(rejectedStampbook);
    }

    @Test
    void reject_담당지역관리자가반려하면잠금뒤상태전이와감사를기록한다() {
        RejectRegionAdminStampbookResult result = useCase.reject(
            USER_ID,
            new RejectRegionAdminStampbookUseCase.RejectRegionAdminStampbookCommand(
                STAMPBOOK_ID,
                "  완료 보상 정책을 확인해 주세요.  "
            ),
            REQUEST_ID
        );

        assertThat(result).isEqualTo(new RejectRegionAdminStampbookResult(
            STAMPBOOK_ID,
            StampbookStatus.DRAFT,
            REJECTED_AT
        ));
        InOrder order = inOrder(
            regionAdminAuthorizationService,
            stampbookService,
            couponPolicyService,
            stampbookContentService,
            contentService,
            recordAuditEventUseCase
        );
        order.verify(regionAdminAuthorizationService).requireAuthorizedRegionAdminForUpdate(USER_ID);
        order.verify(stampbookService).findStampbook(STAMPBOOK_ID);
        order.verify(couponPolicyService).findForUpdate(COUPON_POLICY_ID);
        order.verify(stampbookService).findForUpdate(STAMPBOOK_ID);
        order.verify(stampbookContentService).findContentIds(STAMPBOOK_ID);
        order.verify(contentService).findForUpdate(CONTENT_ID);
        order.verify(stampbookService).findCurrentDatabaseTime();
        order.verify(stampbookService).reject(lockedStampbook);
        order.verify(recordAuditEventUseCase).record(any(AuditEventCommand.class));

        ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
        org.mockito.Mockito.verify(recordAuditEventUseCase).record(captor.capture());
        AuditEventCommand auditEvent = captor.getValue();
        assertThat(auditEvent.result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(auditEvent.previousState()).isEqualTo(StampbookStatus.PENDING_REVIEW.name());
        assertThat(auditEvent.nextState()).isEqualTo(StampbookStatus.DRAFT.name());
        assertThat(auditEvent.reason()).isEqualTo("완료 보상 정책을 확인해 주세요.");
        assertThat(auditEvent.occurredAt()).isEqualTo(REJECTED_AT);
    }

    @Test
    void reject_잠금뒤상태가변경됐으면실패감사를등록하고상태충돌을반환한다() {
        when(lockedStampbook.getStatus()).thenReturn(StampbookStatus.DRAFT);
        when(stampbookService.reject(lockedStampbook)).thenThrow(
            new BusinessException(ErrorCode.STAMPBOOK_STATE_CONFLICT)
        );

        assertThatThrownBy(() -> useCase.reject(
            USER_ID,
            new RejectRegionAdminStampbookUseCase.RejectRegionAdminStampbookCommand(
                STAMPBOOK_ID,
                "완료 보상 정책을 확인해 주세요."
            ),
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STAMPBOOK_STATE_CONFLICT)
        );

        ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
        org.mockito.Mockito.verify(recordFailedAuditEventUseCase).record(captor.capture());
        AuditEventCommand auditEvent = captor.getValue();
        assertThat(auditEvent.result()).isEqualTo(AuditEventResult.FAILURE);
        assertThat(auditEvent.previousState()).isEqualTo(StampbookStatus.DRAFT.name());
        assertThat(auditEvent.nextState()).isNull();
        assertThat(auditEvent.reasonCode()).isEqualTo(ErrorCode.STAMPBOOK_STATE_CONFLICT.code());
    }

    @Test
    void reject_반려사유가비어있으면상태변경과감사를시도하지않는다() {
        assertThatThrownBy(() -> useCase.reject(
            USER_ID,
            new RejectRegionAdminStampbookUseCase.RejectRegionAdminStampbookCommand(
                STAMPBOOK_ID,
                "   "
            ),
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
        );

        verifyNoInteractions(
            regionAdminAuthorizationService,
            couponPolicyService,
            stampbookService,
            stampbookContentService,
            contentService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase
        );
    }

    private AuthorizedRegionAdmin authorizedRegionAdmin() {
        AppUser user = mock(AppUser.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        UserRoleAssignment roleAssignment = mock(UserRoleAssignment.class);
        when(roleAssignment.getRoleAssignmentId()).thenReturn(801L);
        when(roleAssignment.getAppUser()).thenReturn(user);
        when(roleAssignment.getRole()).thenReturn(UserRole.REGION_ADMIN);
        return new AuthorizedRegionAdmin(user, region, roleAssignment);
    }

    private Stampbook stampbook(StampbookStatus status) {
        Stampbook stampbook = mock(Stampbook.class);
        when(stampbook.getStampbookId()).thenReturn(STAMPBOOK_ID);
        when(stampbook.getRegion()).thenReturn(region);
        when(stampbook.getStatus()).thenReturn(status);

        CouponPolicy rewardCouponPolicy = mock(CouponPolicy.class);
        when(rewardCouponPolicy.getCouponPolicyId()).thenReturn(COUPON_POLICY_ID);
        when(stampbook.getRewardCouponPolicy()).thenReturn(rewardCouponPolicy);
        return stampbook;
    }
}
