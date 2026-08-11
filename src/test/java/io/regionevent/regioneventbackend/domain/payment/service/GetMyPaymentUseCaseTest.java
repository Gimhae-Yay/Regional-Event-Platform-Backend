package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetMyPaymentUseCaseTest {

    private static final Long USER_ID = 7L;
    private static final Long PAYMENT_ID = 11L;

    @Test
    void find_whenActiveUserOwnsPayment_returnsPayment() {
        AppUserService appUserService = mock(AppUserService.class);
        PaymentReadService paymentReadService = mock(PaymentReadService.class);
        AppUser user = mock(AppUser.class);
        Payment expected = mock(Payment.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(paymentReadService.findOwnedByPaymentId(USER_ID, PAYMENT_ID)).thenReturn(expected);
        GetMyPaymentUseCase useCase = new GetMyPaymentUseCase(appUserService, paymentReadService);

        Payment actual = useCase.find(USER_ID, PAYMENT_ID);

        assertThat(actual).isSameAs(expected);
        verify(appUserService).findActiveUser(USER_ID);
        verify(paymentReadService).findOwnedByPaymentId(USER_ID, PAYMENT_ID);
    }

    @Test
    void find_whenUserIsNotActive_doesNotReadPayment() {
        AppUserService appUserService = mock(AppUserService.class);
        PaymentReadService paymentReadService = mock(PaymentReadService.class);
        when(appUserService.findActiveUser(USER_ID)).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );
        GetMyPaymentUseCase useCase = new GetMyPaymentUseCase(appUserService, paymentReadService);

        assertThatThrownBy(() -> useCase.find(USER_ID, PAYMENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(appUserService).findActiveUser(USER_ID);
        verifyNoInteractions(paymentReadService);
    }
}
