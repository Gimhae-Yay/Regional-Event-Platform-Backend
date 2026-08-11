package io.regionevent.regioneventbackend.domain.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponRedemptionService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.payment.dto.CreateRefundRequest;
import io.regionevent.regioneventbackend.domain.payment.dto.CreateRefundResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneLookupException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;

class CreateRefundUseCaseTest {

    @Test
    void PortOne_호출_전에_환불과_대기_시도를_커밋한다() {
        PlatformAdminAuthorizationService authorizationService = mock(PlatformAdminAuthorizationService.class);
        PaymentService paymentService = mock(PaymentService.class);
        RefundService refundService = mock(RefundService.class);
        RefundAttemptService refundAttemptService = mock(RefundAttemptService.class);
        PaymentDiscrepancyService discrepancyService = mock(PaymentDiscrepancyService.class);
        PortOnePaymentGateway paymentGateway = mock(PortOnePaymentGateway.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        CreateRefundUseCase useCase = new CreateRefundUseCase(
            authorizationService,
            paymentService,
            refundService,
            refundAttemptService,
            discrepancyService,
            mock(CouponService.class),
            mock(CouponRedemptionService.class),
            mock(CouponStatusHistoryService.class),
            mock(RecordAuditEventUseCase.class),
            paymentGateway,
            transactionManager
        );
        PlatformAdminAssignment assignment = mock(PlatformAdminAssignment.class);
        Payment payment = mock(Payment.class);
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        Refund refund = mock(Refund.class);
        RefundAttempt attempt = mock(RefundAttempt.class);

        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(authorizationService.requireAuthorizedPlatformAdmin(1L)).thenReturn(assignment);
        when(assignment.getGrade()).thenReturn(PlatformAdminGrade.PLATFORM_ADMIN);
        when(paymentService.findByPaymentIdForUpdate(10L)).thenReturn(Optional.of(payment));
        when(payment.getPaymentId()).thenReturn(10L);
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
        when(discrepancyService.findByPaymentIdForUpdate(10L)).thenReturn(Optional.empty());
        when(paymentGateway.cancelPayment("portone-payment", 10_000L, "관리자 요청"))
            .thenThrow(new PortOneLookupException(new RuntimeException("connection failed")));
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
        org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo("DISCREPANT");
    }
}
