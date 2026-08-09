package io.regionevent.regioneventbackend.domain.coupon.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;

class CouponRedemptionTest {

    private static final Instant REDEEMED_AT = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void 생성자_가격_스냅샷과_다른_홀드의_예약을_거부한다() {
        Coupon coupon = mock(Coupon.class);
        CapacityHold snapshotHold = mock(CapacityHold.class);
        CapacityHold reservationHold = mock(CapacityHold.class);
        ReservationPriceSnapshot reservationPriceSnapshot = mock(ReservationPriceSnapshot.class);
        Reservation reservation = mock(Reservation.class);
        when(reservationPriceSnapshot.getCoupon()).thenReturn(coupon);
        when(reservationPriceSnapshot.getCapacityHold()).thenReturn(snapshotHold);
        when(reservation.getCapacityHold()).thenReturn(reservationHold);
        when(snapshotHold.getHoldId()).thenReturn(1L);
        when(reservationHold.getHoldId()).thenReturn(2L);

        assertThatThrownBy(() -> new CouponRedemption(
            coupon,
            reservationPriceSnapshot,
            reservation,
            REDEEMED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
