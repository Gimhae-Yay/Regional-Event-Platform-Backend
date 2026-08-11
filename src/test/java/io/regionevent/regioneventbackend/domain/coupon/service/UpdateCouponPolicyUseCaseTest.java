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

import tools.jackson.databind.node.JsonNodeFactory;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.coupon.dto.UpdateCouponPolicyRequest;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyUpdateHistory;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyUpdateSnapshot;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService.UpdateCouponPolicyCommand;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class UpdateCouponPolicyUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 10L;
    private static final Long COUPON_POLICY_ID = 300L;
    private static final Instant UPDATED_AT = Instant.parse("2026-08-09T00:00:00.123456Z");
    private static final UUID REQUEST_ID = UUID.fromString("7cd03c0a-6ba6-42dc-9f9a-1c1d1fdd9c99");

    private final AppUserService appUserService = mock(AppUserService.class);
    private final OperatorAuthorizationService operatorAuthorizationService = mock(
        OperatorAuthorizationService.class
    );
    private final CouponPolicyService couponPolicyService = mock(CouponPolicyService.class);
    private final CouponPolicyUpdateHistoryService couponPolicyUpdateHistoryService = mock(
        CouponPolicyUpdateHistoryService.class
    );
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase = mock(
        RecordFailedAuditEventUseCase.class
    );
    private final Clock clock = mock(Clock.class);
    private final UpdateCouponPolicyUseCase useCase = new UpdateCouponPolicyUseCase(
        appUserService,
        operatorAuthorizationService,
        couponPolicyService,
        couponPolicyUpdateHistoryService,
        recordAuditEventUseCase,
        recordFailedAuditEventUseCase,
        clock
    );

    @Test
    void update_DRAFT_정책이면_부분수정한다() {
        AuthorizedOperator operator = authorizedOperator();
        CouponPolicy draftPolicy = couponPolicy(CouponPolicyStatus.DRAFT, true);
        CouponPolicy updatedPolicy = couponPolicy(CouponPolicyStatus.DRAFT, true);
        when(updatedPolicy.getName()).thenReturn("수정 정책");
        when(updatedPolicy.getDescription()).thenReturn(null);
        when(updatedPolicy.getDiscountAmount()).thenReturn(4_000L);
        when(appUserService.findActiveUserForUpdate(USER_ID)).thenReturn(Optional.of(operator.user()));
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(USER_ID)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(draftPolicy);
        when(couponPolicyService.update(eq(draftPolicy), any(UpdateCouponPolicyCommand.class), eq(UPDATED_AT)))
            .thenReturn(updatedPolicy);
        AuditEvent auditEvent = auditEvent();
        when(recordAuditEventUseCase.record(any())).thenReturn(auditEvent);
        when(clock.instant()).thenReturn(UPDATED_AT);

        UpdateCouponPolicyResult result = useCase.update(
            USER_ID,
            COUPON_POLICY_ID,
            request("수정 정책", 4_000L, null),
            REQUEST_ID
        );

        assertThat(result.status()).isEqualTo(CouponPolicyStatus.DRAFT);
        assertThat(result.updatedAt()).isEqualTo(UPDATED_AT);
        ArgumentCaptor<UpdateCouponPolicyCommand> commandCaptor = ArgumentCaptor.forClass(
            UpdateCouponPolicyCommand.class
        );
        verify(couponPolicyService).update(eq(draftPolicy), commandCaptor.capture(), eq(UPDATED_AT));
        assertThat(commandCaptor.getValue().name()).isEqualTo("수정 정책");
        assertThat(commandCaptor.getValue().description()).isNull();
        assertThat(commandCaptor.getValue().discountAmount()).isEqualTo(4_000L);
        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(auditCaptor.getValue().reason()).isEqualTo("수정 사유");
        ArgumentCaptor<CouponPolicyUpdateHistory> historyCaptor = ArgumentCaptor.forClass(
            CouponPolicyUpdateHistory.class
        );
        verify(couponPolicyUpdateHistoryService).create(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getPreviousName()).isEqualTo("기존 정책");
        assertThat(historyCaptor.getValue().getNextName()).isEqualTo("수정 정책");
        verifyNoInteractions(recordFailedAuditEventUseCase);
    }

    @Test
    void update_DRAFT가_아닌_정책이면_상태충돌을_반환한다() {
        prepareAuthorizedOperator();
        CouponPolicy publishedPolicy = couponPolicy(CouponPolicyStatus.PUBLISHED, true);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(publishedPolicy);

        assertThatThrownBy(() -> useCase.update(
            USER_ID,
            COUPON_POLICY_ID,
            request("수정 정책", null, null),
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COUPON_POLICY_CONFLICT)
        );

        verify(couponPolicyService, never()).update(any(), any(), any());
        verifyFailureAudit(ErrorCode.COUPON_POLICY_CONFLICT, publishedPolicy);
    }

    @Test
    void update_다른_운영자의_정책이면_권한오류를_반환한다() {
        prepareAuthorizedOperator();
        CouponPolicy otherOperatorPolicy = couponPolicy(CouponPolicyStatus.DRAFT, false);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(otherOperatorPolicy);

        assertThatThrownBy(() -> useCase.update(
            USER_ID,
            COUPON_POLICY_ID,
            request("수정 정책", null, null),
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
        );

        verify(couponPolicyService, never()).update(any(), any(), any());
        verifyFailureAudit(ErrorCode.FORBIDDEN, otherOperatorPolicy);
    }

    @Test
    void update_변경필드가_없으면_입력오류를_반환하고_상태를_변경하지_않는다() {
        assertThatThrownBy(() -> useCase.update(
            USER_ID,
            COUPON_POLICY_ID,
            new UpdateCouponPolicyRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                JsonNodeFactory.instance.stringNode("수정 사유")
            ),
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
        );

        verifyNoInteractions(
            appUserService,
            operatorAuthorizationService,
            couponPolicyService,
            couponPolicyUpdateHistoryService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase
        );
    }

    @Test
    void update_사유가_없으면_입력_오류를_반환하고_상태를_변경하지_않는다() {
        assertThatThrownBy(() -> useCase.update(
            USER_ID,
            COUPON_POLICY_ID,
            new UpdateCouponPolicyRequest(
                JsonNodeFactory.instance.stringNode("수정 쿠폰"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            ),
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
        );

        verifyNoInteractions(
            appUserService,
            operatorAuthorizationService,
            couponPolicyService,
            couponPolicyUpdateHistoryService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase
        );
    }

    @Test
    void update_변경값이_기존_정책과_같으면_이력과_성공_감사를_남기지_않는다() {
        prepareAuthorizedOperator();
        CouponPolicy draftPolicy = couponPolicy(CouponPolicyStatus.DRAFT, true);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(draftPolicy);

        UpdateCouponPolicyResult result = useCase.update(
            USER_ID,
            COUPON_POLICY_ID,
            request(draftPolicy.getName(), draftPolicy.getDiscountAmount(), draftPolicy.getDescription()),
            REQUEST_ID
        );

        assertThat(result.updatedAt()).isEqualTo(Instant.parse("2026-08-08T00:00:00Z"));
        verify(couponPolicyService, never()).update(any(), any(), any());
        verifyNoInteractions(couponPolicyUpdateHistoryService, recordAuditEventUseCase);
        verifyNoInteractions(recordFailedAuditEventUseCase);
    }

    @Test
    void update_병합한_금액조건이_잘못되면_입력오류를_반환한다() {
        prepareAuthorizedOperator();
        CouponPolicy draftPolicy = couponPolicy(CouponPolicyStatus.DRAFT, true);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(draftPolicy);

        assertThatThrownBy(() -> useCase.update(
            USER_ID,
            COUPON_POLICY_ID,
            request("수정 정책", 12_000L, null),
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
        );

        verify(couponPolicyService, never()).update(any(), any(), any());
    }

    @Test
    void update_병합한_기간조건이_잘못되면_입력오류를_반환한다() {
        prepareAuthorizedOperator();
        CouponPolicy draftPolicy = couponPolicy(CouponPolicyStatus.DRAFT, true);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(draftPolicy);

        assertThatThrownBy(() -> useCase.update(
            USER_ID,
            COUPON_POLICY_ID,
            new UpdateCouponPolicyRequest(
                null,
                null,
                null,
                null,
                null,
                JsonNodeFactory.instance.stringNode("2026-09-01T00:00:00Z"),
                null,
                null,
                JsonNodeFactory.instance.stringNode("수정 사유")
            ),
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
        );

        verify(couponPolicyService, never()).update(any(), any(), any());
    }

    @Test
    void updateHistory_운영자_역할이_아니면_생성할_수_없다() {
        CouponPolicy couponPolicy = couponPolicy(CouponPolicyStatus.DRAFT, true);
        AuditEvent auditEvent = auditEvent();
        when(auditEvent.getActorRole()).thenReturn(UserRole.VISITOR.name());

        assertThatThrownBy(() -> new CouponPolicyUpdateHistory(
            couponPolicy,
            auditEvent,
            UserRole.VISITOR.name(),
            "수정 사유",
            REQUEST_ID.toString(),
            UPDATED_AT,
            CouponPolicyUpdateSnapshot.from(couponPolicy),
            CouponPolicyUpdateSnapshot.from(couponPolicy)
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("actorRole must be OPERATOR");
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
    }

    private void prepareAuthorizedOperator() {
        AuthorizedOperator operator = authorizedOperator();
        when(appUserService.findActiveUserForUpdate(USER_ID)).thenReturn(Optional.of(operator.user()));
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(USER_ID)).thenReturn(operator);
        when(clock.instant()).thenReturn(UPDATED_AT);
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
        when(assignment.getRole()).thenReturn(UserRole.OPERATOR);
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
        when(couponPolicy.getName()).thenReturn("기존 정책");
        when(couponPolicy.getDescription()).thenReturn("기존 설명");
        when(couponPolicy.getDiscountAmount()).thenReturn(3_000L);
        when(couponPolicy.getMinimumPaymentAmount()).thenReturn(10_000L);
        when(couponPolicy.getValidDays()).thenReturn(30);
        when(couponPolicy.getIssueStartsAt()).thenReturn(Instant.parse("2026-08-01T00:00:00Z"));
        when(couponPolicy.getIssueEndsAt()).thenReturn(Instant.parse("2026-08-31T00:00:00Z"));
        when(couponPolicy.getTotalIssueLimit()).thenReturn(1_000L);
        when(couponPolicy.getUpdatedAt()).thenReturn(Instant.parse("2026-08-08T00:00:00Z"));
        when(content.isOwnedBy(USER_ID)).thenReturn(ownedByOperator);
        when(content.isScopedTo(REGION_ID)).thenReturn(true);
        when(region.getRegionId()).thenReturn(REGION_ID);
        return couponPolicy;
    }

    private static UpdateCouponPolicyRequest request(
        String name,
        Long discountAmount,
        String description
    ) {
        return new UpdateCouponPolicyRequest(
            name == null ? null : JsonNodeFactory.instance.stringNode(name),
            description == null ? JsonNodeFactory.instance.nullNode() : JsonNodeFactory.instance.stringNode(description),
            discountAmount == null ? null : JsonNodeFactory.instance.numberNode(discountAmount),
            null,
            null,
            null,
            null,
            null,
            JsonNodeFactory.instance.stringNode("수정 사유")
        );
    }

    private AuditEvent auditEvent() {
        AuditEvent auditEvent = mock(AuditEvent.class);
        when(auditEvent.getResult()).thenReturn(AuditEventResult.SUCCESS);
        when(auditEvent.getTargetType()).thenReturn(AuditEventTargetType.COUPON_POLICY);
        when(auditEvent.getTargetId()).thenReturn(COUPON_POLICY_ID);
        when(auditEvent.getPreviousState()).thenReturn(CouponPolicyStatus.DRAFT.name());
        when(auditEvent.getNextState()).thenReturn(CouponPolicyStatus.DRAFT.name());
        when(auditEvent.getActorKind()).thenReturn("USER");
        when(auditEvent.getActorRole()).thenReturn(UserRole.OPERATOR.name());
        when(auditEvent.getReason()).thenReturn("수정 사유");
        when(auditEvent.getRequestId()).thenReturn(REQUEST_ID.toString());
        when(auditEvent.getOccurredAt()).thenReturn(UPDATED_AT);
        return auditEvent;
    }
}
