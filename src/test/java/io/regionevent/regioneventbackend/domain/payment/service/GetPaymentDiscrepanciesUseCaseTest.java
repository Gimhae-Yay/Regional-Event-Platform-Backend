package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetPaymentDiscrepanciesUseCaseTest {

    private static final Long ACTOR_USER_ID = 101L;

    @Test
    void get_활성전체관리자_상태별불일치와가격스냅샷정보를반환한다() {
        PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        PaymentDiscrepancyService paymentDiscrepancyService = mock(PaymentDiscrepancyService.class);
        PaymentDiscrepancy discrepancy = discrepancy();
        when(paymentDiscrepancyService.findAllByStatus("OPEN")).thenReturn(List.of(discrepancy));
        GetPaymentDiscrepanciesUseCase useCase = new GetPaymentDiscrepanciesUseCase(
            authorizationService,
            paymentDiscrepancyService
        );

        List<PaymentDiscrepancyListInfo> actual = useCase.get(ACTOR_USER_ID, "OPEN");

        assertThat(actual).containsExactly(new PaymentDiscrepancyListInfo(
            301L,
            902L,
            "AMOUNT_MISMATCH",
            "OPEN",
            15_000L,
            "KRW",
            Instant.parse("2026-08-06T03:05:00Z")
        ));
        verify(authorizationService).requireAuthorizedPlatformAdmin(ACTOR_USER_ID);
        verify(paymentDiscrepancyService).findAllByStatus("OPEN");
    }

    @Test
    void get_권한없는사용자_불일치를조회하지않는다() {
        PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        PaymentDiscrepancyService paymentDiscrepancyService = mock(PaymentDiscrepancyService.class);
        when(authorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID)).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );
        GetPaymentDiscrepanciesUseCase useCase = new GetPaymentDiscrepanciesUseCase(
            authorizationService,
            paymentDiscrepancyService
        );

        assertThatThrownBy(() -> useCase.get(ACTOR_USER_ID, "OPEN"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(authorizationService).requireAuthorizedPlatformAdmin(ACTOR_USER_ID);
        verifyNoInteractions(paymentDiscrepancyService);
    }

    private PaymentDiscrepancy discrepancy() {
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        Payment payment = mock(Payment.class);
        PaymentDiscrepancy discrepancy = mock(PaymentDiscrepancy.class);
        when(snapshot.getFinalAmount()).thenReturn(15_000L);
        when(snapshot.getCurrency()).thenReturn("KRW");
        when(payment.getPaymentId()).thenReturn(902L);
        when(payment.getReservationPriceSnapshot()).thenReturn(snapshot);
        when(discrepancy.getPaymentDiscrepancyId()).thenReturn(301L);
        when(discrepancy.getPayment()).thenReturn(payment);
        when(discrepancy.getDiscrepancyType()).thenReturn("AMOUNT_MISMATCH");
        when(discrepancy.getStatus()).thenReturn("OPEN");
        when(discrepancy.getDetectedAt()).thenReturn(Instant.parse("2026-08-06T03:05:00Z"));
        return discrepancy;
    }
}
