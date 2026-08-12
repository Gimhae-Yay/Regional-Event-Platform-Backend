package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptInitiatorKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptOutcomeKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetRefundFailureUseCaseTest {

    private static final Long ACTOR_USER_ID = 101L;
    private static final Long REFUND_ID = 552L;

    @Test
    void get_활성플랫폼관리자는읽기전용으로환불상세와시도이력을반환한다() {
        PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        RefundService refundService = mock(RefundService.class);
        RefundAttemptService refundAttemptService = mock(RefundAttemptService.class);
        Refund refund = mock(Refund.class);
        RefundAttempt firstAttempt = mock(RefundAttempt.class);
        RefundAttempt secondAttempt = mock(RefundAttempt.class);
        stubRefund(refund);
        stubAttempt(firstAttempt, 701L, 1);
        stubAttempt(secondAttempt, 702L, 2);
        when(refundService.findByRefundId(REFUND_ID)).thenReturn(refund);
        when(refundAttemptService.findAllByRefundId(REFUND_ID)).thenReturn(
            List.of(firstAttempt, secondAttempt)
        );
        GetRefundFailureUseCase useCase = new GetRefundFailureUseCase(
            authorizationService,
            refundService,
            refundAttemptService
        );

        RefundFailureDetailInfo actual = useCase.get(ACTOR_USER_ID, REFUND_ID);

        assertThat(actual).isNotNull();
        verify(authorizationService).requireAuthorizedPlatformAdmin(ACTOR_USER_ID);
        verify(refundService).findByRefundId(REFUND_ID);
        verify(refundAttemptService).findAllByRefundId(REFUND_ID);
        verifyNoMoreInteractions(authorizationService, refundService, refundAttemptService);
    }

    @Test
    void get_권한이없으면환불과시도이력을조회하지않는다() {
        PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        RefundService refundService = mock(RefundService.class);
        RefundAttemptService refundAttemptService = mock(RefundAttemptService.class);
        when(authorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID)).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );
        GetRefundFailureUseCase useCase = new GetRefundFailureUseCase(
            authorizationService,
            refundService,
            refundAttemptService
        );

        assertThatThrownBy(() -> useCase.get(ACTOR_USER_ID, REFUND_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(authorizationService).requireAuthorizedPlatformAdmin(ACTOR_USER_ID);
        verifyNoInteractions(refundService, refundAttemptService);
    }

    private void stubRefund(Refund refund) {
        Reservation reservation = mock(Reservation.class);
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        Payment payment = mock(Payment.class);
        when(reservation.getReservationId()).thenReturn(124L);
        when(snapshot.getCurrency()).thenReturn("KRW");
        when(snapshot.getFinalAmount()).thenReturn(12_000L);
        when(payment.getPaymentId()).thenReturn(903L);
        when(payment.getReservation()).thenReturn(reservation);
        when(payment.getReservationPriceSnapshot()).thenReturn(snapshot);
        when(payment.getOrderId()).thenReturn("ORD-20260807-3K9P1M");
        when(refund.getRefundId()).thenReturn(REFUND_ID);
        when(refund.getPayment()).thenReturn(payment);
        when(refund.getAmount()).thenReturn(12_000L);
        when(refund.getStatus()).thenReturn(RefundStatus.DISCREPANT);
        when(refund.getRequestedAt()).thenReturn(Instant.parse("2026-08-07T01:10:00Z"));
    }

    private void stubAttempt(
        RefundAttempt attempt,
        Long refundAttemptId,
        int attemptNo
    ) {
        when(attempt.getRefundAttemptId()).thenReturn(refundAttemptId);
        when(attempt.getAttemptNo()).thenReturn(attemptNo);
        when(attempt.getInitiatorKind()).thenReturn(RefundAttemptInitiatorKind.SYSTEM);
        when(attempt.getOutcomeKind()).thenReturn(RefundAttemptOutcomeKind.NO_RESPONSE);
        when(attempt.getAttemptedAt()).thenReturn(Instant.parse("2026-08-07T01:10:31Z"));
    }
}
