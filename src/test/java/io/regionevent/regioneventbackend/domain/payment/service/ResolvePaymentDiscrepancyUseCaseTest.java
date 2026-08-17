package io.regionevent.regioneventbackend.domain.payment.service;

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
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.payment.dto.ResolvePaymentDiscrepancyRequest;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ResolvePaymentDiscrepancyUseCaseTest {

    private static final Instant RESOLVED_AT = Instant.parse("2026-08-12T01:02:03Z");
    private static final UUID REQUEST_ID = UUID.fromString("06f74b1f-9d68-4219-bdcb-d3992ce93f4f");

    @Test
    void resolve_OPEN불일치를종결하고조치와감사를기록한다() {
        PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        PaymentDiscrepancyService discrepancyService = mock(PaymentDiscrepancyService.class);
        PaymentDiscrepancyActionService actionService = mock(PaymentDiscrepancyActionService.class);
        RecordAuditEventUseCase auditEventUseCase = mock(RecordAuditEventUseCase.class);
        ResolvePaymentDiscrepancyUseCase useCase = useCase(
            authorizationService,
            discrepancyService,
            actionService,
            auditEventUseCase
        );
        PlatformAdminAssignment assignment = authorizedAssignment();
        PaymentDiscrepancy discrepancy = mock(PaymentDiscrepancy.class);
        Payment payment = mock(Payment.class);
        CapacityHold hold = mock(CapacityHold.class);
        Region region = mock(Region.class);
        when(authorizationService.requireAuthorizedPlatformAdminForUpdate(1L)).thenReturn(assignment);
        when(discrepancyService.findByIdForUpdate(301L)).thenReturn(Optional.of(discrepancy));
        when(discrepancy.getStatus()).thenReturn("OPEN");
        when(discrepancy.getPaymentDiscrepancyId()).thenReturn(301L);
        when(discrepancy.getPayment()).thenReturn(payment);
        when(payment.getCapacityHold()).thenReturn(hold);
        when(hold.getRegion()).thenReturn(region);

        ResolvePaymentDiscrepancyResult result = useCase.resolve(
            1L,
            301L,
            new ResolvePaymentDiscrepancyRequest("  PortOne 재조회 #4821  ", "  금액 일치를 확인  "),
            REQUEST_ID
        );

        assertThat(result).isEqualTo(new ResolvePaymentDiscrepancyResult(
            301L,
            "RESOLVED_NO_ISSUE",
            RESOLVED_AT
        ));
        verify(discrepancy).resolveNoIssue();
        verify(actionService).create(
            discrepancy,
            "NO_ISSUE_CLOSE",
            "PortOne 재조회 #4821",
            "MANUAL_NO_ISSUE_CLOSE",
            "RESOLVED_NO_ISSUE",
            RESOLVED_AT
        );
        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(auditEventUseCase).record(auditCaptor.capture());
        AuditEventCommand audit = auditCaptor.getValue();
        assertThat(audit.requestId()).isEqualTo(REQUEST_ID);
        assertThat(audit.region()).isSameAs(region);
        assertThat(audit.targetType()).isEqualTo(AuditEventTargetType.PAYMENT_DISCREPANCY);
        assertThat(audit.targetId()).isEqualTo(301L);
        assertThat(audit.previousState()).isEqualTo("OPEN");
        assertThat(audit.nextState()).isEqualTo("RESOLVED_NO_ISSUE");
        assertThat(audit.result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(audit.reasonCode()).isEqualTo("MANUAL_NO_ISSUE_CLOSE");
        assertThat(audit.reason()).isEqualTo("금액 일치를 확인");
        assertThat(audit.evidenceReference()).isEqualTo("PortOne 재조회 #4821");
        assertThat(audit.occurredAt()).isEqualTo(RESOLVED_AT);
        verify(payment, never()).approve(any(), any(), any());
        verify(payment, never()).decline(any());
        verify(payment, never()).expire(any());
        verify(payment, never()).markDiscrepant(any(), any());
    }

    @Test
    void resolve_OPEN이아닌불일치는상태충돌로거부하고이력을남기지않는다() {
        PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        PaymentDiscrepancyService discrepancyService = mock(PaymentDiscrepancyService.class);
        PaymentDiscrepancyActionService actionService = mock(PaymentDiscrepancyActionService.class);
        RecordAuditEventUseCase auditEventUseCase = mock(RecordAuditEventUseCase.class);
        ResolvePaymentDiscrepancyUseCase useCase = useCase(
            authorizationService,
            discrepancyService,
            actionService,
            auditEventUseCase
        );
        PaymentDiscrepancy discrepancy = mock(PaymentDiscrepancy.class);
        PlatformAdminAssignment assignment = authorizedAssignment();
        when(authorizationService.requireAuthorizedPlatformAdminForUpdate(1L)).thenReturn(assignment);
        when(discrepancyService.findByIdForUpdate(301L)).thenReturn(Optional.of(discrepancy));
        when(discrepancy.getStatus()).thenReturn("REFUND_REQUESTED");

        assertThatThrownBy(() -> useCase.resolve(
            1L,
            301L,
            new ResolvePaymentDiscrepancyRequest("증빙", "사유"),
            REQUEST_ID
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.PAYMENT_DISCREPANCY_STATE_CONFLICT);

        verify(discrepancy, never()).resolveNoIssue();
        verifyNoInteractions(actionService, auditEventUseCase);
    }

    @Test
    void resolve_입력값이공백이거나500자를초과하면조회하지않고거부한다() {
        PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        PaymentDiscrepancyService discrepancyService = mock(PaymentDiscrepancyService.class);
        PaymentDiscrepancyActionService actionService = mock(PaymentDiscrepancyActionService.class);
        RecordAuditEventUseCase auditEventUseCase = mock(RecordAuditEventUseCase.class);
        ResolvePaymentDiscrepancyUseCase useCase = useCase(
            authorizationService,
            discrepancyService,
            actionService,
            auditEventUseCase
        );

        assertThatThrownBy(() -> useCase.resolve(
            1L,
            301L,
            new ResolvePaymentDiscrepancyRequest(" ", "a".repeat(501)),
            REQUEST_ID
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(authorizationService, discrepancyService, actionService, auditEventUseCase);
    }

    @Test
    void resolve_권한없는사용자는불일치를조회하거나변경하지않는다() {
        PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        PaymentDiscrepancyService discrepancyService = mock(PaymentDiscrepancyService.class);
        PaymentDiscrepancyActionService actionService = mock(PaymentDiscrepancyActionService.class);
        RecordAuditEventUseCase auditEventUseCase = mock(RecordAuditEventUseCase.class);
        ResolvePaymentDiscrepancyUseCase useCase = useCase(
            authorizationService,
            discrepancyService,
            actionService,
            auditEventUseCase
        );
        when(authorizationService.requireAuthorizedPlatformAdminForUpdate(2L))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> useCase.resolve(
            2L,
            301L,
            new ResolvePaymentDiscrepancyRequest("증빙", "사유"),
            REQUEST_ID
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(discrepancyService, actionService, auditEventUseCase);
    }

    private ResolvePaymentDiscrepancyUseCase useCase(
        PlatformAdminAuthorizationService authorizationService,
        PaymentDiscrepancyService discrepancyService,
        PaymentDiscrepancyActionService actionService,
        RecordAuditEventUseCase auditEventUseCase
    ) {
        return new ResolvePaymentDiscrepancyUseCase(
            authorizationService,
            discrepancyService,
            actionService,
            auditEventUseCase,
            Clock.fixed(RESOLVED_AT, ZoneOffset.UTC)
        );
    }

    private PlatformAdminAssignment authorizedAssignment() {
        PlatformAdminAssignment assignment = mock(PlatformAdminAssignment.class);
        AppUser appUser = mock(AppUser.class);
        when(assignment.getPlatformAdminAssignmentId()).thenReturn(101L);
        when(assignment.getAppUser()).thenReturn(appUser);
        when(assignment.isActive()).thenReturn(true);
        when(assignment.getGrade()).thenReturn(PlatformAdminGrade.PLATFORM_ADMIN);
        when(appUser.getUserId()).thenReturn(1L);
        when(appUser.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(appUser.getAccountKind()).thenReturn(AppUserAccountKind.PRIVILEGED);
        return assignment;
    }
}
