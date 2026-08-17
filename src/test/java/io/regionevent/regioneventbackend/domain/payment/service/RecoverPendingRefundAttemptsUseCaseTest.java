package io.regionevent.regioneventbackend.domain.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.service.RestoreCouponUseCase;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptOutcomeKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundFailureReasonCode;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneLookupException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;

@ExtendWith(MockitoExtension.class)
class RecoverPendingRefundAttemptsUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Test
    void recover_확인된_성공_취소면_기존_시도를_응답으로_확정하고_환불을_성공으로_전이한다() {
        Fixture fixture = new Fixture();
        PortOnePaymentGateway.PortOneCancellation cancellation = new PortOnePaymentGateway.PortOneCancellation(
            "cancel-1",
            "SUCCEEDED",
            "result-hash"
        );
        when(fixture.paymentGateway.findByPaymentId("portone-payment"))
            .thenReturn(new PortOnePaymentGateway.PortOnePayment(
                "portone-payment",
                "transaction-1",
                null,
                10_000L,
                "KRW",
                "DECLINED",
                "payment-hash",
                cancellation
            ));
        RecoverPendingRefundAttemptsUseCase.RecoveryResult result = fixture.useCase.recover();

        verify(fixture.attempt).respond("cancel-1", "SUCCEEDED", "result-hash");
        verify(fixture.refund).succeed(NOW);
        verify(fixture.refund, never()).fail(any());
        verify(fixture.refund, never()).markDiscrepant(any());
        verify(fixture.paymentGateway, never()).cancelPayment(any(), any(Long.class), any());
        verify(fixture.auditEventUseCase).record(any(AuditEventCommand.class));
        org.assertj.core.api.Assertions.assertThat(result)
            .isEqualTo(new RecoverPendingRefundAttemptsUseCase.RecoveryResult(1, 1, 0));
    }

    @Test
    void recover_취소_확인이_없으면_기존_시도를_응답으로_확정하고_환불을_실패로_전이한다() {
        Fixture fixture = new Fixture();
        when(fixture.paymentGateway.findByPaymentId("portone-payment"))
            .thenReturn(new PortOnePaymentGateway.PortOnePayment(
                "portone-payment",
                "transaction-1",
                null,
                10_000L,
                "KRW",
                "PAID",
                "payment-hash"
            ));

        RecoverPendingRefundAttemptsUseCase.RecoveryResult result = fixture.useCase.recover();

        verify(fixture.attempt).respond(null, "PAID", "payment-hash");
        verify(fixture.refund).fail(NOW);
        verify(fixture.paymentGateway, never()).cancelPayment(any(), any(Long.class), any());
        verify(fixture.auditEventUseCase).record(any(AuditEventCommand.class));
        org.assertj.core.api.Assertions.assertThat(result)
            .isEqualTo(new RecoverPendingRefundAttemptsUseCase.RecoveryResult(1, 1, 0));
    }

    @Test
    void recover_재조회에_실패하면_기존_시도를_프로세스_중단으로_확정하고_불일치로_전이한다() {
        Fixture fixture = new Fixture();
        when(fixture.paymentGateway.findByPaymentId("portone-payment"))
            .thenThrow(new PortOneLookupException(new IllegalStateException("lookup failed")));

        RecoverPendingRefundAttemptsUseCase.RecoveryResult result = fixture.useCase.recover();

        verify(fixture.attempt).noResponse(RefundFailureReasonCode.PROCESS_INTERRUPTED);
        verify(fixture.refund).markDiscrepant(NOW);
        verify(fixture.paymentGateway, never()).cancelPayment(any(), any(Long.class), any());
        verify(fixture.auditEventUseCase).record(any(AuditEventCommand.class));
        org.assertj.core.api.Assertions.assertThat(result)
            .isEqualTo(new RecoverPendingRefundAttemptsUseCase.RecoveryResult(1, 1, 1));
    }

    private static class Fixture {

        private final RefundAttemptService refundAttemptService = mock(RefundAttemptService.class);
        private final RefundService refundService = mock(RefundService.class);
        private final RestoreCouponUseCase restoreCouponUseCase = mock(RestoreCouponUseCase.class);
        private final RecordAuditEventUseCase auditEventUseCase = mock(RecordAuditEventUseCase.class);
        private final PortOnePaymentGateway paymentGateway = mock(PortOnePaymentGateway.class);
        private final Refund refund = mock(Refund.class);
        private final RefundAttempt attempt = mock(RefundAttempt.class);
        private final Payment payment = mock(Payment.class);
        private final CapacityHold capacityHold = mock(CapacityHold.class);
        private final RecoverPendingRefundAttemptsUseCase useCase;

        private Fixture() {
            PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
            when(transactionManager.getTransaction(any())).thenAnswer(invocation -> transactionStatus());
            when(refundAttemptService.findRecoveryCandidates(NOW.minusSeconds(60))).thenReturn(List.of(
                new RefundAttemptService.RecoveryCandidate(2L, 1L, "portone-payment")
            ));
            when(refundService.findByRefundIdForUpdate(1L)).thenReturn(Optional.of(refund));
            when(refundAttemptService.findByRefundAttemptIdForUpdate(2L)).thenReturn(Optional.of(attempt));
            when(refund.getRefundId()).thenReturn(1L);
            when(refund.getStatus()).thenReturn(RefundStatus.PROCESSING);
            when(refund.getPayment()).thenReturn(payment);
            when(payment.getCapacityHold()).thenReturn(capacityHold);
            when(attempt.getOutcomeKind()).thenReturn(RefundAttemptOutcomeKind.PENDING);
            useCase = new RecoverPendingRefundAttemptsUseCase(
                refundAttemptService,
                refundService,
                restoreCouponUseCase,
                auditEventUseCase,
                paymentGateway,
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager
            );
        }

        private TransactionStatus transactionStatus() {
            return new SimpleTransactionStatus();
        }
    }
}
