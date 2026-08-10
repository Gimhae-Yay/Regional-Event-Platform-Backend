package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.mission.service.MissionService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.stampbook.service.StampbookService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class EndCouponPolicyUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 10L;
    private static final Long COUPON_POLICY_ID = 300L;
    private static final Instant ENDED_AT = Instant.parse("2026-08-10T00:00:00.123456Z");
    private static final UUID REQUEST_ID = UUID.fromString("7cd03c0a-6ba6-42dc-9f9a-1c1d1fdd9580");

    private final AppUserService appUserService = mock(AppUserService.class);
    private final OperatorAuthorizationService operatorAuthorizationService = mock(
        OperatorAuthorizationService.class
    );
    private final CouponPolicyService couponPolicyService = mock(CouponPolicyService.class);
    private final MissionService missionService = mock(MissionService.class);
    private final StampbookService stampbookService = mock(StampbookService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final Clock clock = mock(Clock.class);
    private final EndCouponPolicyUseCase useCase = new EndCouponPolicyUseCase(
        appUserService,
        operatorAuthorizationService,
        couponPolicyService,
        missionService,
        stampbookService,
        recordAuditEventUseCase,
        clock
    );

    @Test
    void end_PUBLISHED_정책이면_종료하고_감사_이력을_기록한다() {
        AuthorizedOperator operator = prepareAuthorizedOperator();
        CouponPolicy publishedPolicy = couponPolicy(CouponPolicyStatus.PUBLISHED, true, null);
        CouponPolicy endedPolicy = couponPolicy(CouponPolicyStatus.ENDED, true, ENDED_AT);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(publishedPolicy);
        when(couponPolicyService.end(publishedPolicy, ENDED_AT)).thenReturn(endedPolicy);
        when(clock.instant()).thenReturn(ENDED_AT);

        EndCouponPolicyResult result = useCase.end(
            USER_ID,
            COUPON_POLICY_ID,
            "  프로모션 기간 종료  ",
            REQUEST_ID
        );

        assertThat(result).isEqualTo(new EndCouponPolicyResult(
            COUPON_POLICY_ID,
            CouponPolicyStatus.ENDED,
            ENDED_AT
        ));
        ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(captor.capture());
        assertThat(captor.getValue()).satisfies(command -> {
            assertThat(command.requestId()).isEqualTo(REQUEST_ID);
            assertThat(command.targetType()).isEqualTo(AuditEventTargetType.COUPON_POLICY);
            assertThat(command.targetId()).isEqualTo(COUPON_POLICY_ID);
            assertThat(command.previousState()).isEqualTo(CouponPolicyStatus.PUBLISHED.name());
            assertThat(command.nextState()).isEqualTo(CouponPolicyStatus.ENDED.name());
            assertThat(command.result()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(command.reason()).isEqualTo("프로모션 기간 종료");
            assertThat(command.occurredAt()).isEqualTo(ENDED_AT);
            assertThat(command.actor().roleAssignment()).isEqualTo(operator.roleAssignment());
        });
    }

    @Test
    void end_이미_종료된_정책이면_기존_결과를_반환하고_상태를_변경하지_않는다() {
        prepareAuthorizedOperator();
        CouponPolicy endedPolicy = couponPolicy(CouponPolicyStatus.ENDED, true, ENDED_AT);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(endedPolicy);

        EndCouponPolicyResult result = useCase.end(
            USER_ID,
            COUPON_POLICY_ID,
            "프로모션 기간 종료",
            REQUEST_ID
        );

        assertThat(result.status()).isEqualTo(CouponPolicyStatus.ENDED);
        assertThat(result.endedAt()).isEqualTo(ENDED_AT);
        verify(couponPolicyService, never()).end(any(), any());
        verifyNoInteractions(missionService, stampbookService, recordAuditEventUseCase, clock);
    }

    @Test
    void end_공개_미션이_참조하면_충돌을_반환하고_상태를_변경하지_않는다() {
        prepareAuthorizedOperator();
        CouponPolicy publishedPolicy = couponPolicy(CouponPolicyStatus.PUBLISHED, true, null);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(publishedPolicy);
        when(missionService.existsPublishedRewardCouponPolicy(COUPON_POLICY_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.end(
            USER_ID,
            COUPON_POLICY_ID,
            "프로모션 기간 종료",
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COUPON_POLICY_REFERENCED)
        );

        verify(couponPolicyService, never()).end(any(), any());
        verifyNoInteractions(stampbookService, recordAuditEventUseCase, clock);
    }

    @Test
    void end_공개_스탬프북이_참조하면_충돌을_반환하고_상태를_변경하지_않는다() {
        prepareAuthorizedOperator();
        CouponPolicy publishedPolicy = couponPolicy(CouponPolicyStatus.PUBLISHED, true, null);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(publishedPolicy);
        when(stampbookService.existsPublishedRewardCouponPolicy(COUPON_POLICY_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.end(
            USER_ID,
            COUPON_POLICY_ID,
            "프로모션 기간 종료",
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COUPON_POLICY_REFERENCED)
        );

        verify(couponPolicyService, never()).end(any(), any());
        verifyNoInteractions(recordAuditEventUseCase, clock);
    }

    @Test
    void end_PUBLISHED가_아닌_정책이면_상태_충돌을_반환한다() {
        prepareAuthorizedOperator();
        CouponPolicy draftPolicy = couponPolicy(CouponPolicyStatus.DRAFT, true, null);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(draftPolicy);

        assertThatThrownBy(() -> useCase.end(
            USER_ID,
            COUPON_POLICY_ID,
            "프로모션 기간 종료",
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COUPON_POLICY_CONFLICT)
        );

        verify(couponPolicyService, never()).end(any(), any());
        verifyNoInteractions(missionService, stampbookService, recordAuditEventUseCase, clock);
    }

    @Test
    void end_다른_운영자의_정책이면_권한_오류를_반환한다() {
        prepareAuthorizedOperator();
        CouponPolicy otherOperatorPolicy = couponPolicy(CouponPolicyStatus.PUBLISHED, false, null);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(otherOperatorPolicy);

        assertThatThrownBy(() -> useCase.end(
            USER_ID,
            COUPON_POLICY_ID,
            "프로모션 기간 종료",
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
        );

        verify(couponPolicyService, never()).end(any(), any());
        verifyNoInteractions(missionService, stampbookService, recordAuditEventUseCase, clock);
    }

    @Test
    void end_대상이_없으면_대상_없음_오류를_반환한다() {
        prepareAuthorizedOperator();
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> useCase.end(
            USER_ID,
            COUPON_POLICY_ID,
            "프로모션 기간 종료",
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
        );

        verifyNoInteractions(missionService, stampbookService, recordAuditEventUseCase, clock);
    }

    @Test
    void end_유효하지_않은_사유면_입력_오류를_반환하고_의존성을_호출하지_않는다() {
        assertThatThrownBy(() -> useCase.end(USER_ID, COUPON_POLICY_ID, " ", REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );

        verifyNoInteractions(
            appUserService,
            operatorAuthorizationService,
            couponPolicyService,
            missionService,
            stampbookService,
            recordAuditEventUseCase,
            clock
        );
    }

    private AuthorizedOperator prepareAuthorizedOperator() {
        AuthorizedOperator operator = authorizedOperator();
        when(appUserService.findActiveUserForUpdate(USER_ID)).thenReturn(Optional.of(operator.user()));
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(USER_ID)).thenReturn(operator);
        return operator;
    }

    private AuthorizedOperator authorizedOperator() {
        AppUser user = mock(AppUser.class);
        Region region = mock(Region.class);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(assignment.getRoleAssignmentId()).thenReturn(1L);
        when(assignment.getAppUser()).thenReturn(user);
        return new AuthorizedOperator(user, region, assignment);
    }

    private CouponPolicy couponPolicy(
        CouponPolicyStatus status,
        boolean ownedByOperator,
        Instant endedAt
    ) {
        CouponPolicy couponPolicy = mock(CouponPolicy.class);
        Content content = mock(Content.class);
        Region region = mock(Region.class);
        when(couponPolicy.getCouponPolicyId()).thenReturn(COUPON_POLICY_ID);
        when(couponPolicy.getContent()).thenReturn(content);
        when(couponPolicy.getRegion()).thenReturn(region);
        when(couponPolicy.getStatus()).thenReturn(status);
        when(couponPolicy.getEndedAt()).thenReturn(endedAt);
        when(content.isOwnedBy(USER_ID)).thenReturn(ownedByOperator);
        when(content.isScopedTo(REGION_ID)).thenReturn(true);
        when(region.getRegionId()).thenReturn(REGION_ID);
        return couponPolicy;
    }
}
