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
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class PublishCouponPolicyUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 10L;
    private static final Long COUPON_POLICY_ID = 300L;
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-08T00:00:00.123456Z");
    private static final UUID REQUEST_ID = UUID.fromString("7cd03c0a-6ba6-42dc-9f9a-1c1d1fdd9c99");

    private final AppUserService appUserService = mock(AppUserService.class);
    private final OperatorAuthorizationService operatorAuthorizationService = mock(
        OperatorAuthorizationService.class
    );
    private final CouponPolicyService couponPolicyService = mock(CouponPolicyService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase = mock(
        RecordFailedAuditEventUseCase.class
    );
    private final Clock clock = mock(Clock.class);
    private final PublishCouponPolicyUseCase useCase = new PublishCouponPolicyUseCase(
        appUserService,
        operatorAuthorizationService,
        couponPolicyService,
        recordAuditEventUseCase,
        recordFailedAuditEventUseCase,
        clock
    );

    @Test
    void publish_DRAFT_정책이면_공개하고_감사_이력을_기록한다() {
        AuthorizedOperator operator = authorizedOperator();
        CouponPolicy draftPolicy = couponPolicy(CouponPolicyStatus.DRAFT, true);
        CouponPolicy publishedPolicy = couponPolicy(CouponPolicyStatus.PUBLISHED, true);
        when(appUserService.findActiveUserForUpdate(USER_ID)).thenReturn(Optional.of(operator.user()));
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(USER_ID)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(draftPolicy);
        when(couponPolicyService.publish(draftPolicy, PUBLISHED_AT)).thenReturn(publishedPolicy);
        when(clock.instant()).thenReturn(PUBLISHED_AT);

        PublishCouponPolicyResult result = useCase.publish(
            USER_ID,
            COUPON_POLICY_ID,
            "  검토 완료 후 공개  ",
            REQUEST_ID
        );

        assertThat(result.status()).isEqualTo(CouponPolicyStatus.PUBLISHED);
        assertThat(result.publishedAt()).isEqualTo(PUBLISHED_AT);
        ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(captor.capture());
        assertThat(captor.getValue().targetType()).isEqualTo(AuditEventTargetType.COUPON_POLICY);
        assertThat(captor.getValue().previousState()).isEqualTo(CouponPolicyStatus.DRAFT.name());
        assertThat(captor.getValue().nextState()).isEqualTo(CouponPolicyStatus.PUBLISHED.name());
        assertThat(captor.getValue().result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(captor.getValue().reason()).isEqualTo("검토 완료 후 공개");
    }

    @Test
    void publish_이미_공개된_정책이면_기존_결과를_반환하고_이력을_추가하지_않는다() {
        AuthorizedOperator operator = authorizedOperator();
        CouponPolicy policy = couponPolicy(CouponPolicyStatus.PUBLISHED, true);
        when(appUserService.findActiveUserForUpdate(USER_ID)).thenReturn(Optional.of(operator.user()));
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(USER_ID)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(policy);

        PublishCouponPolicyResult result = useCase.publish(
            USER_ID,
            COUPON_POLICY_ID,
            "검토 완료 후 공개",
            REQUEST_ID
        );

        assertThat(result.status()).isEqualTo(CouponPolicyStatus.PUBLISHED);
        verify(couponPolicyService, never()).publish(any(), any());
        verify(recordAuditEventUseCase, never()).record(any());
        verify(recordFailedAuditEventUseCase, never()).record(any());
    }

    @Test
    void publish_종료된_정책이면_상태충돌을_반환한다() {
        prepareAuthorizedOperator();
        CouponPolicy endedPolicy = couponPolicy(CouponPolicyStatus.ENDED, true);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID))
            .thenReturn(endedPolicy);

        assertThatThrownBy(() -> useCase.publish(USER_ID, COUPON_POLICY_ID, "공개", REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COUPON_POLICY_CONFLICT)
            );

        verify(couponPolicyService, never()).publish(any(), any());
        verify(recordAuditEventUseCase, never()).record(any());
        verifyFailureAudit(ErrorCode.COUPON_POLICY_CONFLICT, endedPolicy);
    }

    @Test
    void publish_다른_운영자의_정책이면_권한오류를_반환한다() {
        prepareAuthorizedOperator();
        CouponPolicy otherOperatorPolicy = couponPolicy(CouponPolicyStatus.DRAFT, false);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID))
            .thenReturn(otherOperatorPolicy);

        assertThatThrownBy(() -> useCase.publish(USER_ID, COUPON_POLICY_ID, "공개", REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(couponPolicyService, never()).publish(any(), any());
        verify(recordAuditEventUseCase, never()).record(any());
        verifyFailureAudit(ErrorCode.FORBIDDEN, otherOperatorPolicy);
    }

    @Test
    void publish_대상이_없으면_대상없음을_반환한다() {
        prepareAuthorizedOperator();
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> useCase.publish(USER_ID, COUPON_POLICY_ID, "공개", REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );

        verify(recordAuditEventUseCase, never()).record(any());
    }

    @Test
    void publish_공개사유가_501자면_입력오류를_반환하고_상태와_감사이력을_변경하지_않는다() {
        assertThatThrownBy(() -> useCase.publish(
            USER_ID,
            COUPON_POLICY_ID,
            "가".repeat(501),
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
        );

        verifyNoInteractions(
            appUserService,
            operatorAuthorizationService,
            couponPolicyService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase
        );
    }

    private void verifyFailureAudit(ErrorCode errorCode, CouponPolicy couponPolicy) {
        ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordFailedAuditEventUseCase).record(captor.capture());
        AuditEventCommand command = captor.getValue();
        assertThat(command.targetType()).isEqualTo(AuditEventTargetType.COUPON_POLICY);
        assertThat(command.targetId()).isEqualTo(COUPON_POLICY_ID);
        assertThat(command.previousState()).isEqualTo(couponPolicy.getStatus().name());
        assertThat(command.nextState()).isNull();
        assertThat(command.result()).isEqualTo(AuditEventResult.FAILURE);
        assertThat(command.reasonCode()).isEqualTo(errorCode.code());
        assertThat(command.actor()).isNotNull();
    }

    private void prepareAuthorizedOperator() {
        AuthorizedOperator operator = authorizedOperator();
        when(appUserService.findActiveUserForUpdate(USER_ID)).thenReturn(Optional.of(operator.user()));
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(USER_ID)).thenReturn(operator);
        when(clock.instant()).thenReturn(PUBLISHED_AT);
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
        boolean ownedByOperator
    ) {
        CouponPolicy couponPolicy = mock(CouponPolicy.class);
        Content content = mock(Content.class);
        Region region = mock(Region.class);
        when(couponPolicy.getCouponPolicyId()).thenReturn(COUPON_POLICY_ID);
        when(couponPolicy.getContent()).thenReturn(content);
        when(couponPolicy.getRegion()).thenReturn(region);
        when(couponPolicy.getStatus()).thenReturn(status);
        when(couponPolicy.getPublishedAt()).thenReturn(PUBLISHED_AT);
        when(content.isOwnedBy(USER_ID)).thenReturn(ownedByOperator);
        when(content.isScopedTo(REGION_ID)).thenReturn(true);
        when(region.getRegionId()).thenReturn(REGION_ID);
        return couponPolicy;
    }
}
