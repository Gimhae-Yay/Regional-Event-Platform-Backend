package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.service.RestoreCouponUseCase;
import io.regionevent.regioneventbackend.domain.payment.dto.RetryRefundResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptInitiatorKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundFailureReasonCode;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneNoResponseException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneResponseException;
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

class RetryRefundUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Test
    void retry_두번째_시도를_외부_호출_전에_기록하고_성공으로_확정한다() {
        Fixture fixture = new Fixture(RefundStatus.SUCCEEDED);
        when(fixture.paymentGateway.cancelPayment("portone-payment", 10_000L, "MANUAL_REFUND_RETRY"))
            .thenReturn(new PortOnePaymentGateway.PortOneCancellation(
                "cancel-2",
                "SUCCEEDED",
                "result-hash"
            ));

        RetryRefundResponse response = fixture.useCase.retry(1L, "20", UUID.randomUUID());

        org.mockito.InOrder inOrder = inOrder(fixture.transactionManager, fixture.paymentGateway);
        inOrder.verify(fixture.transactionManager).commit(any());
        inOrder.verify(fixture.paymentGateway).cancelPayment(
            "portone-payment",
            10_000L,
            "MANUAL_REFUND_RETRY"
        );
        verify(fixture.refund).retry();
        verify(fixture.attemptService).create(any(RefundAttempt.class));
        verify(fixture.newAttempt).respond("cancel-2", "SUCCEEDED", "result-hash");
        verify(fixture.refund).succeed(NOW);
        assertThat(response).isEqualTo(new RetryRefundResponse("20", 2, "SUCCEEDED", NOW));
    }

    @Test
    void retry_세번_시도한_환불은_외부_호출_없이_거부한다() {
        Fixture fixture = new Fixture(RefundStatus.FAILED);
        RefundAttempt thirdAttempt = mock(RefundAttempt.class);
        when(thirdAttempt.getAttemptNo()).thenReturn(3);
        when(fixture.attemptService.findAllByRefundId(20L)).thenReturn(List.of(thirdAttempt));

        assertThatThrownBy(() -> fixture.useCase.retry(1L, "20", UUID.randomUUID()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.REFUND_STATE_CONFLICT);

        verify(fixture.paymentGateway, never()).cancelPayment(any(), any(Long.class), any());
        verify(fixture.refund, never()).retry();
        verify(fixture.attemptService, never()).create(any());
    }

    @Test
    void retry_응답을_받지_못하면_불일치로_확정한다() {
        Fixture fixture = new Fixture(RefundStatus.DISCREPANT);
        when(fixture.paymentGateway.cancelPayment("portone-payment", 10_000L, "MANUAL_REFUND_RETRY"))
            .thenThrow(new PortOneNoResponseException(
                RefundFailureReasonCode.CONNECTION,
                new IllegalStateException("connection failed")
            ));

        RetryRefundResponse response = fixture.useCase.retry(1L, "20", UUID.randomUUID());

        verify(fixture.newAttempt).noResponse(RefundFailureReasonCode.CONNECTION);
        verify(fixture.refund).markDiscrepant(NOW);
        assertThat(response).isEqualTo(new RetryRefundResponse("20", 2, "DISCREPANT", NOW));
    }

    @Test
    void retry_명시적_실패_응답이면_환불을_실패로_확정한다() {
        Fixture fixture = new Fixture(RefundStatus.FAILED);
        when(fixture.paymentGateway.cancelPayment("portone-payment", 10_000L, "MANUAL_REFUND_RETRY"))
            .thenReturn(new PortOnePaymentGateway.PortOneCancellation(
                "cancel-2",
                "FAILED",
                "result-hash"
            ));

        RetryRefundResponse response = fixture.useCase.retry(1L, "20", UUID.randomUUID());

        verify(fixture.newAttempt).respond("cancel-2", "FAILED", "result-hash");
        verify(fixture.refund).fail(NOW);
        assertThat(response).isEqualTo(new RetryRefundResponse("20", 2, "FAILED", NOW));
    }

    @Test
    void retry_PortOne_수신_오류면_응답_시도를_불일치로_확정하고_오류를_전파한다() {
        Fixture fixture = new Fixture(RefundStatus.DISCREPANT);
        when(fixture.paymentGateway.cancelPayment("portone-payment", 10_000L, "MANUAL_REFUND_RETRY"))
            .thenThrow(new PortOneResponseException(
                "HTTP_500",
                "result-hash",
                new IllegalStateException("invalid response")
            ));

        assertThatThrownBy(() -> fixture.useCase.retry(1L, "20", UUID.randomUUID()))
            .isInstanceOf(PortOneResponseException.class);

        verify(fixture.newAttempt).respond(null, "HTTP_500", "result-hash");
        verify(fixture.refund).markDiscrepant(NOW);
    }

    @Test
    void retry_양수가_아닌_식별자는_형식_오류로_거부한다() {
        Fixture fixture = new Fixture(RefundStatus.FAILED);

        assertThatThrownBy(() -> fixture.useCase.retry(1L, "0", UUID.randomUUID()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_TYPE);
    }

    @Test
    void retry_Long_범위를_초과한_식별자는_형식_오류로_거부한다() {
        Fixture fixture = new Fixture(RefundStatus.FAILED);

        assertThatThrownBy(() -> fixture.useCase.retry(1L, "9223372036854775808", UUID.randomUUID()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_TYPE);
    }

    @Test
    void retry_failed_상태가_아닌_환불은_외부_호출_없이_거부한다() {
        Fixture fixture = new Fixture(RefundStatus.SUCCEEDED, RefundStatus.SUCCEEDED);

        assertThatThrownBy(() -> fixture.useCase.retry(1L, "20", UUID.randomUUID()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.REFUND_STATE_CONFLICT);

        verify(fixture.paymentGateway, never()).cancelPayment(any(), any(Long.class), any());
    }

    private static class Fixture {

        private final PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        private final RefundService refundService = mock(RefundService.class);
        private final RefundAttemptService attemptService = mock(RefundAttemptService.class);
        private final RestoreCouponUseCase restoreCouponUseCase = mock(RestoreCouponUseCase.class);
        private final RecordAuditEventUseCase auditEventUseCase = mock(RecordAuditEventUseCase.class);
        private final PortOnePaymentGateway paymentGateway = mock(PortOnePaymentGateway.class);
        private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        private final Refund refund = mock(Refund.class);
        private final RefundAttempt previousAttempt = mock(RefundAttempt.class);
        private final RefundAttempt newAttempt = mock(RefundAttempt.class);
        private final RetryRefundUseCase useCase;

        private Fixture(RefundStatus completedStatus) {
            this(RefundStatus.FAILED, completedStatus);
        }

        private Fixture(
            RefundStatus initialStatus,
            RefundStatus completedStatus
        ) {
            PlatformAdminAssignment assignment = mock(PlatformAdminAssignment.class);
            AppUser appUser = mock(AppUser.class);
            Payment payment = mock(Payment.class);
            CapacityHold capacityHold = mock(CapacityHold.class);
            Region region = mock(Region.class);

            when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            when(authorizationService.requireAuthorizedPlatformAdminForUpdate(1L)).thenReturn(assignment);
            when(assignment.getPlatformAdminAssignmentId()).thenReturn(1L);
            when(assignment.getAppUser()).thenReturn(appUser);
            when(assignment.getGrade()).thenReturn(PlatformAdminGrade.PLATFORM_ADMIN);
            when(assignment.isActive()).thenReturn(true);
            when(appUser.getUserId()).thenReturn(1L);
            when(appUser.getStatus()).thenReturn(AppUserStatus.ACTIVE);
            when(appUser.getAccountKind()).thenReturn(AppUserAccountKind.PRIVILEGED);
            when(refundService.findByRefundIdForUpdate(20L)).thenReturn(Optional.of(refund));
            when(refund.getRefundId()).thenReturn(20L);
            when(refund.getStatus()).thenReturn(initialStatus, completedStatus);
            when(refund.getPayment()).thenReturn(payment);
            when(payment.getPortonePaymentId()).thenReturn("portone-payment");
            when(payment.getCapacityHold()).thenReturn(capacityHold);
            when(capacityHold.getRegion()).thenReturn(region);
            when(refund.getAmount()).thenReturn(10_000L);
            when(attemptService.findAllByRefundId(20L)).thenReturn(List.of(previousAttempt));
            when(previousAttempt.getAttemptNo()).thenReturn(1);
            when(attemptService.create(any(RefundAttempt.class))).thenReturn(newAttempt);
            when(newAttempt.getRefundAttemptId()).thenReturn(30L);
            when(newAttempt.getAttemptNo()).thenReturn(2);
            when(newAttempt.getAttemptedAt()).thenReturn(NOW);
            when(attemptService.findByRefundAttemptIdForUpdate(30L)).thenReturn(Optional.of(newAttempt));
            when(payment.getReservation()).thenReturn(null);
            useCase = new RetryRefundUseCase(
                authorizationService,
                refundService,
                attemptService,
                restoreCouponUseCase,
                auditEventUseCase,
                paymentGateway,
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager
            );
        }
    }
}
