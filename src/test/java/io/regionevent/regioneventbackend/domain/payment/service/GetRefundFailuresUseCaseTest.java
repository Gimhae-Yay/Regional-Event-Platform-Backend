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
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetRefundFailuresUseCaseTest {

    private static final Long ACTOR_USER_ID = 101L;

    @Test
    void get_활성플랫폼관리자는_읽기전용으로_시도수와갱신시각순의_환불목록을반환한다() {
        PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        RefundService refundService = mock(RefundService.class);
        RefundAttemptService refundAttemptService = mock(RefundAttemptService.class);
        Refund laterRefund = refund(
            552L,
            Instant.parse("2026-08-07T01:10:00Z"),
            Instant.parse("2026-08-07T01:11:00Z")
        );
        Refund earlierRefund = refund(551L, Instant.parse("2026-08-07T01:00:00Z"));
        Refund sameTimeEarlierIdRefund = refund(550L, Instant.parse("2026-08-07T01:00:00Z"));
        RefundAttempt attempt = attempt(552L, Instant.parse("2026-08-07T01:10:31Z"));
        when(refundService.findAllByStatuses(Set.of(RefundStatus.FAILED, RefundStatus.DISCREPANT)))
            .thenReturn(List.of(laterRefund, earlierRefund, sameTimeEarlierIdRefund));
        when(refundAttemptService.findAllByRefundIds(List.of(552L, 551L, 550L)))
            .thenReturn(List.of(attempt));
        GetRefundFailuresUseCase useCase = new GetRefundFailuresUseCase(
            authorizationService,
            refundService,
            refundAttemptService
        );

        List<RefundFailureListInfo> actual = useCase.get(
            ACTOR_USER_ID,
            Set.of(RefundStatus.FAILED, RefundStatus.DISCREPANT)
        );

        assertThat(actual).extracting(RefundFailureListInfo::refundId).containsExactly(550L, 551L, 552L);
        assertThat(actual.get(2).attemptCount()).isOne();
        assertThat(actual.get(2).updatedAt()).isEqualTo(Instant.parse("2026-08-07T01:11:00Z"));
        verify(authorizationService).requireAuthorizedPlatformAdmin(ACTOR_USER_ID);
        verify(refundService).findAllByStatuses(Set.of(RefundStatus.FAILED, RefundStatus.DISCREPANT));
        verify(refundAttemptService).findAllByRefundIds(List.of(552L, 551L, 550L));
        verifyNoMoreInteractions(authorizationService, refundService, refundAttemptService);
    }

    @Test
    void get_예약없는실패및불일치환불이면_예약식별자가없는목록을반환한다() {
        PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        RefundService refundService = mock(RefundService.class);
        RefundAttemptService refundAttemptService = mock(RefundAttemptService.class);
        Refund failedRefund = refund(551L, Instant.parse("2026-08-07T01:00:00Z"));
        Refund discrepantRefund = refund(552L, Instant.parse("2026-08-07T01:10:00Z"));
        when(failedRefund.getStatus()).thenReturn(RefundStatus.FAILED);
        when(failedRefund.getPayment().getReservation()).thenReturn(null);
        when(discrepantRefund.getPayment().getReservation()).thenReturn(null);
        when(refundService.findAllByStatuses(Set.of(RefundStatus.FAILED, RefundStatus.DISCREPANT)))
            .thenReturn(List.of(failedRefund, discrepantRefund));
        when(refundAttemptService.findAllByRefundIds(List.of(551L, 552L))).thenReturn(List.of());
        GetRefundFailuresUseCase useCase = new GetRefundFailuresUseCase(
            authorizationService,
            refundService,
            refundAttemptService
        );

        List<RefundFailureListInfo> actual = useCase.get(
            ACTOR_USER_ID,
            Set.of(RefundStatus.FAILED, RefundStatus.DISCREPANT)
        );

        assertThat(actual).extracting(RefundFailureListInfo::refundId).containsExactly(551L, 552L);
        assertThat(actual).extracting(RefundFailureListInfo::reservationId).containsOnlyNulls();
        verify(authorizationService).requireAuthorizedPlatformAdmin(ACTOR_USER_ID);
        verify(refundService).findAllByStatuses(Set.of(RefundStatus.FAILED, RefundStatus.DISCREPANT));
        verify(refundAttemptService).findAllByRefundIds(List.of(551L, 552L));
        verifyNoMoreInteractions(authorizationService, refundService, refundAttemptService);
    }

    @Test
    void get_권한이없으면_환불을조회하지않는다() {
        PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        RefundService refundService = mock(RefundService.class);
        RefundAttemptService refundAttemptService = mock(RefundAttemptService.class);
        when(authorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID)).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );
        GetRefundFailuresUseCase useCase = new GetRefundFailuresUseCase(
            authorizationService,
            refundService,
            refundAttemptService
        );

        assertThatThrownBy(() -> useCase.get(ACTOR_USER_ID, Set.of(RefundStatus.FAILED)))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(authorizationService).requireAuthorizedPlatformAdmin(ACTOR_USER_ID);
        verifyNoInteractions(refundService, refundAttemptService);
    }

    private Refund refund(Long refundId, Instant requestedAt) {
        return refund(refundId, requestedAt, null);
    }

    private Refund refund(
        Long refundId,
        Instant requestedAt,
        Instant resolvedAt
    ) {
        Reservation reservation = mock(Reservation.class);
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        Payment payment = mock(Payment.class);
        Refund refund = mock(Refund.class);
        when(reservation.getReservationId()).thenReturn(124L);
        when(snapshot.getCurrency()).thenReturn("KRW");
        when(payment.getPaymentId()).thenReturn(903L);
        when(payment.getReservation()).thenReturn(reservation);
        when(payment.getReservationPriceSnapshot()).thenReturn(snapshot);
        when(refund.getRefundId()).thenReturn(refundId);
        when(refund.getPayment()).thenReturn(payment);
        when(refund.getAmount()).thenReturn(12_000L);
        when(refund.getStatus()).thenReturn(RefundStatus.DISCREPANT);
        when(refund.getRequestedAt()).thenReturn(requestedAt);
        when(refund.getResolvedAt()).thenReturn(resolvedAt);
        return refund;
    }

    private RefundAttempt attempt(Long refundId, Instant attemptedAt) {
        Refund refund = mock(Refund.class);
        RefundAttempt attempt = mock(RefundAttempt.class);
        when(refund.getRefundId()).thenReturn(refundId);
        when(attempt.getRefund()).thenReturn(refund);
        when(attempt.getAttemptedAt()).thenReturn(attemptedAt);
        return attempt;
    }
}
