package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class PaymentReadServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long OTHER_USER_ID = 8L;
    private static final Long PAYMENT_ID = 11L;

    @Test
    void findOwnedByPaymentId_whenOwnerMatches_returnsPaymentWithoutWriting() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        Payment payment = paymentOwnedBy(USER_ID);
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        PaymentReadService service = new PaymentReadService(paymentRepository);

        Payment actual = service.findOwnedByPaymentId(USER_ID, PAYMENT_ID);

        assertThat(actual).isSameAs(payment);
        verify(paymentRepository).findByPaymentId(PAYMENT_ID);
        verifyNoMoreInteractions(paymentRepository);
    }

    @Test
    void findOwnedByPaymentId_whenOwnerDoesNotMatch_throwsForbidden() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        Payment payment = paymentOwnedBy(OTHER_USER_ID);
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        PaymentReadService service = new PaymentReadService(paymentRepository);

        assertThatThrownBy(() -> service.findOwnedByPaymentId(USER_ID, PAYMENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(paymentRepository).findByPaymentId(PAYMENT_ID);
        verifyNoMoreInteractions(paymentRepository);
    }

    @Test
    void findOwnedByPaymentId_whenPaymentDoesNotExist_throwsNotFound() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());
        PaymentReadService service = new PaymentReadService(paymentRepository);

        assertThatThrownBy(() -> service.findOwnedByPaymentId(USER_ID, PAYMENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );

        verify(paymentRepository).findByPaymentId(PAYMENT_ID);
        verifyNoMoreInteractions(paymentRepository);
    }

    private Payment paymentOwnedBy(Long ownerId) {
        AppUser owner = mock(AppUser.class);
        CapacityHold capacityHold = mock(CapacityHold.class);
        Payment payment = mock(Payment.class);
        when(owner.getUserId()).thenReturn(ownerId);
        when(capacityHold.getUser()).thenReturn(owner);
        when(payment.getCapacityHold()).thenReturn(capacityHold);
        return payment;
    }
}
