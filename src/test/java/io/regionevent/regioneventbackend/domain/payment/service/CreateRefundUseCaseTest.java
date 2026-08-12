package io.regionevent.regioneventbackend.domain.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponRedemptionService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.payment.dto.CreateRefundRequest;
import io.regionevent.regioneventbackend.domain.payment.dto.CreateRefundResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptInitiatorKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundFailureReasonCode;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneNoResponseException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;

class CreateRefundUseCaseTest {

    @Test
    void 예약취소환불_PortOne성공응답_환불을성공으로확정하고감사를기록한다() {
        ReservationCancellationRefundFixture fixture = reservationCancellationRefundFixture(RefundStatus.SUCCEEDED);
        when(fixture.paymentGateway().cancelPayment("portone-payment", 10_000L, "예약 취소"))
            .thenReturn(new PortOnePaymentGateway.PortOneCancellation("cancel-1", "SUCCEEDED", "hash-1"));

        CreateRefundResponse response = fixture.useCase().createForReservationCancellation(
            10L,
            null,
            UUID.randomUUID()
        );

        verify(fixture.attempt()).respond("cancel-1", "SUCCEEDED", "hash-1");
        verify(fixture.refund()).succeed(Instant.parse("2026-08-11T00:00:00Z"));
        verify(fixture.auditEventUseCase()).record(any(AuditEventCommand.class));
        org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void 예약취소환불_PortOne명시실패응답_환불을실패로확정하고감사를기록한다() {
        ReservationCancellationRefundFixture fixture = reservationCancellationRefundFixture(RefundStatus.FAILED);
        when(fixture.paymentGateway().cancelPayment("portone-payment", 10_000L, "예약 취소"))
            .thenReturn(new PortOnePaymentGateway.PortOneCancellation("cancel-1", "FAILED", "hash-1"));

        CreateRefundResponse response = fixture.useCase().createForReservationCancellation(
            10L,
            null,
            UUID.randomUUID()
        );

        verify(fixture.attempt()).respond("cancel-1", "FAILED", "hash-1");
        verify(fixture.refund()).fail(Instant.parse("2026-08-11T00:00:00Z"));
        verify(fixture.auditEventUseCase()).record(any(AuditEventCommand.class));
        org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo("FAILED");
    }

    @Test
    void 예약취소환불_PortOne무응답_환불을불일치로확정하고감사를기록한다() {
        ReservationCancellationRefundFixture fixture = reservationCancellationRefundFixture(RefundStatus.DISCREPANT);
        when(fixture.paymentGateway().cancelPayment("portone-payment", 10_000L, "예약 취소"))
            .thenThrow(new PortOneNoResponseException(
                RefundFailureReasonCode.TIMEOUT,
                new RuntimeException("timeout")
            ));

        CreateRefundResponse response = fixture.useCase().createForReservationCancellation(
            10L,
            null,
            UUID.randomUUID()
        );

        verify(fixture.attempt()).noResponse(RefundFailureReasonCode.TIMEOUT);
        verify(fixture.refund()).markDiscrepant(Instant.parse("2026-08-11T00:00:00Z"));
        verify(fixture.auditEventUseCase()).record(any(AuditEventCommand.class));
        org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo("DISCREPANT");
    }

    @Test
    void PortOne_호출_전에_환불과_대기_시도를_커밋한다() {
        PlatformAdminAuthorizationService authorizationService = mock(PlatformAdminAuthorizationService.class);
        PaymentService paymentService = mock(PaymentService.class);
        RefundService refundService = mock(RefundService.class);
        RefundAttemptService refundAttemptService = mock(RefundAttemptService.class);
        PaymentDiscrepancyService discrepancyService = mock(PaymentDiscrepancyService.class);
        PaymentDiscrepancyActionService discrepancyActionService = mock(PaymentDiscrepancyActionService.class);
        PortOnePaymentGateway paymentGateway = mock(PortOnePaymentGateway.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        RecordAuditEventUseCase auditEventUseCase = mock(RecordAuditEventUseCase.class);
        CreateRefundUseCase useCase = new CreateRefundUseCase(
            authorizationService,
            paymentService,
            refundService,
            refundAttemptService,
            discrepancyService,
            discrepancyActionService,
            mock(CouponService.class),
            mock(CouponRedemptionService.class),
            mock(CouponStatusHistoryService.class),
            auditEventUseCase,
            paymentGateway,
            Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC),
            transactionManager
        );
        PlatformAdminAssignment assignment = mock(PlatformAdminAssignment.class);
        AppUser appUser = mock(AppUser.class);
        Payment payment = mock(Payment.class);
        CapacityHold capacityHold = mock(CapacityHold.class);
        Region region = mock(Region.class);
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        Refund refund = mock(Refund.class);
        RefundAttempt attempt = mock(RefundAttempt.class);
        PaymentDiscrepancy discrepancy = mock(PaymentDiscrepancy.class);

        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(authorizationService.requireAuthorizedPlatformAdmin(1L)).thenReturn(assignment);
        when(assignment.getPlatformAdminAssignmentId()).thenReturn(1L);
        when(assignment.getAppUser()).thenReturn(appUser);
        when(assignment.isActive()).thenReturn(true);
        when(assignment.getGrade()).thenReturn(PlatformAdminGrade.PLATFORM_ADMIN);
        when(appUser.getUserId()).thenReturn(1L);
        when(appUser.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(appUser.getAccountKind()).thenReturn(AppUserAccountKind.PRIVILEGED);
        when(paymentService.findByPaymentIdForUpdate(10L)).thenReturn(Optional.of(payment));
        when(payment.getPaymentId()).thenReturn(10L);
        when(payment.getCapacityHold()).thenReturn(capacityHold);
        when(capacityHold.getRegion()).thenReturn(region);
        when(payment.getStatus()).thenReturn(PaymentStatus.APPROVED);
        when(payment.getPortonePaymentId()).thenReturn("portone-payment");
        when(payment.getReservationPriceSnapshot()).thenReturn(snapshot);
        when(snapshot.getFinalAmount()).thenReturn(10_000L);
        when(refundService.findByPaymentIdForUpdate(10L)).thenReturn(Optional.empty());
        when(refundService.create(any())).thenReturn(refund);
        when(refund.getRefundId()).thenReturn(20L);
        when(refund.getAmount()).thenReturn(10_000L);
        when(refundAttemptService.create(any())).thenReturn(attempt);
        when(attempt.getRefundAttemptId()).thenReturn(30L);
        when(discrepancyService.findByPaymentIdForUpdate(10L)).thenReturn(Optional.of(discrepancy));
        when(discrepancy.getStatus()).thenReturn("OPEN");
        when(discrepancy.getPaymentDiscrepancyId()).thenReturn(40L);
        when(paymentGateway.cancelPayment("portone-payment", 10_000L, "관리자 요청"))
            .thenThrow(new PortOneNoResponseException(
                RefundFailureReasonCode.CONNECTION,
                new RuntimeException("connection failed")
            ));
        when(refundService.findByRefundIdForUpdate(20L)).thenReturn(Optional.of(refund));
        when(refundAttemptService.findByRefundAttemptIdForUpdate(30L)).thenReturn(Optional.of(attempt));
        when(refund.getPayment()).thenReturn(payment);
        when(refund.getStatus()).thenReturn(RefundStatus.DISCREPANT);
        when(refund.getRequestedAt()).thenReturn(Instant.parse("2026-08-11T00:00:00Z"));

        CreateRefundResponse response = useCase.create(
            1L,
            "10",
            new CreateRefundRequest("증빙-1", "관리자 요청"),
            UUID.randomUUID()
        );

        InOrder inOrder = inOrder(transactionManager, paymentGateway);
        inOrder.verify(transactionManager).commit(any());
        inOrder.verify(paymentGateway).cancelPayment("portone-payment", 10_000L, "관리자 요청");
        ArgumentCaptor<RefundAttempt> attemptCaptor = ArgumentCaptor.forClass(RefundAttempt.class);
        verify(refundAttemptService).create(attemptCaptor.capture());
        verify(attempt).noResponse(RefundFailureReasonCode.CONNECTION);
        verify(refund).markDiscrepant(Instant.parse("2026-08-11T00:00:00Z"));
        verify(discrepancy).requestRefund();
        verify(discrepancyActionService).create(
            discrepancy,
            "FULL_REFUND_REQUEST",
            "증빙-1",
            "MANUAL_FULL_REFUND",
            "REFUND_REQUESTED",
            Instant.parse("2026-08-11T00:00:00Z")
        );
        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(auditEventUseCase, org.mockito.Mockito.times(2)).record(auditCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(attemptCaptor.getValue().getInitiatorKind())
            .isEqualTo(RefundAttemptInitiatorKind.SYSTEM);
        org.assertj.core.api.Assertions.assertThat(auditCaptor.getAllValues())
            .anySatisfy(audit -> {
                org.assertj.core.api.Assertions.assertThat(audit.targetType())
                    .isEqualTo(io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType.PAYMENT_DISCREPANCY);
                org.assertj.core.api.Assertions.assertThat(audit.targetId()).isEqualTo(40L);
                org.assertj.core.api.Assertions.assertThat(audit.previousState()).isEqualTo("OPEN");
                org.assertj.core.api.Assertions.assertThat(audit.nextState()).isEqualTo("REFUND_REQUESTED");
            });
        org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo("DISCREPANT");
    }

    private ReservationCancellationRefundFixture reservationCancellationRefundFixture(RefundStatus refundStatus) {
        PaymentService paymentService = mock(PaymentService.class);
        RefundService refundService = mock(RefundService.class);
        RefundAttemptService refundAttemptService = mock(RefundAttemptService.class);
        PortOnePaymentGateway paymentGateway = mock(PortOnePaymentGateway.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        RecordAuditEventUseCase auditEventUseCase = mock(RecordAuditEventUseCase.class);
        Payment payment = mock(Payment.class);
        CapacityHold capacityHold = mock(CapacityHold.class);
        Region region = mock(Region.class);
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        Refund refund = mock(Refund.class);
        RefundAttempt attempt = mock(RefundAttempt.class);

        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(paymentService.findByPaymentIdForUpdate(10L)).thenReturn(Optional.of(payment));
        when(payment.getPaymentId()).thenReturn(10L);
        when(payment.getStatus()).thenReturn(PaymentStatus.APPROVED);
        when(payment.getPortonePaymentId()).thenReturn("portone-payment");
        when(payment.getReservationPriceSnapshot()).thenReturn(snapshot);
        when(payment.getCapacityHold()).thenReturn(capacityHold);
        when(capacityHold.getRegion()).thenReturn(region);
        when(snapshot.getFinalAmount()).thenReturn(10_000L);
        when(refundService.findByPaymentIdForUpdate(10L)).thenReturn(Optional.empty());
        when(refundService.create(any())).thenReturn(refund);
        when(refund.getRefundId()).thenReturn(20L);
        when(refund.getAmount()).thenReturn(10_000L);
        when(refund.getPayment()).thenReturn(payment);
        when(refund.getStatus()).thenReturn(refundStatus);
        when(refund.getRequestedAt()).thenReturn(Instant.parse("2026-08-11T00:00:00Z"));
        when(refundAttemptService.create(any())).thenReturn(attempt);
        when(attempt.getRefundAttemptId()).thenReturn(30L);
        when(refundService.findByRefundIdForUpdate(20L)).thenReturn(Optional.of(refund));
        when(refundAttemptService.findByRefundAttemptIdForUpdate(30L)).thenReturn(Optional.of(attempt));

        CreateRefundUseCase useCase = new CreateRefundUseCase(
            mock(PlatformAdminAuthorizationService.class),
            paymentService,
            refundService,
            refundAttemptService,
            mock(PaymentDiscrepancyService.class),
            mock(PaymentDiscrepancyActionService.class),
            mock(CouponService.class),
            mock(CouponRedemptionService.class),
            mock(CouponStatusHistoryService.class),
            auditEventUseCase,
            paymentGateway,
            Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC),
            transactionManager
        );
        return new ReservationCancellationRefundFixture(useCase, paymentGateway, refund, attempt, auditEventUseCase);
    }

    private record ReservationCancellationRefundFixture(
        CreateRefundUseCase useCase,
        PortOnePaymentGateway paymentGateway,
        Refund refund,
        RefundAttempt attempt,
        RecordAuditEventUseCase auditEventUseCase
    ) {
    }
}
