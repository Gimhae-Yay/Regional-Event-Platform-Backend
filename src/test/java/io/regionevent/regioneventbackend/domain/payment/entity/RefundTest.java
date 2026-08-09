package io.regionevent.regioneventbackend.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;

class RefundTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void 생성자_결제_가격_스냅샷_최종_금액과_다른_환불_금액을_거부한다() {
        Payment payment = mock(Payment.class);
        ReservationPriceSnapshot reservationPriceSnapshot = mock(ReservationPriceSnapshot.class);
        when(payment.getReservationPriceSnapshot()).thenReturn(reservationPriceSnapshot);
        when(reservationPriceSnapshot.getFinalAmount()).thenReturn(7_000L);

        assertThatThrownBy(() -> new Refund(payment, 10_000, REQUESTED_AT))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
