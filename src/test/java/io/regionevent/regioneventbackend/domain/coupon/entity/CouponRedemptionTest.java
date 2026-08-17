package io.regionevent.regioneventbackend.domain.coupon.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;

class CouponRedemptionTest {

    private static final Instant REDEEMED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant REVERSED_AT = Instant.parse("2026-08-11T00:01:00Z");

    @Test
    void 무료예약취소_최초반전은예약취소출처와시각을기록한다() {
        RedemptionFixture fixture = redemptionFixture();

        boolean changed = fixture.redemption.reverseForReservationCancellation(REVERSED_AT);

        assertThat(changed).isTrue();
        assertThat(fixture.redemption.getStatus()).isEqualTo(CouponRedemptionStatus.REVERSED);
        assertThat(fixture.redemption.getRefund()).isNull();
        assertThat(fixture.redemption.getReversalReasonCode())
            .isEqualTo(CouponRedemptionReversalReason.RESERVATION_CANCELLED);
        assertThat(fixture.redemption.getReversedAt()).isEqualTo(REVERSED_AT);
    }

    @Test
    void 유료환불_성공한실제환불과사유를기록한다() {
        RedemptionFixture fixture = redemptionFixture();
        Refund refund = succeededRefund(fixture);

        boolean changed = fixture.redemption.reverseForRefund(refund, REVERSED_AT);

        assertThat(changed).isTrue();
        assertThat(fixture.redemption.getRefund()).isSameAs(refund);
        assertThat(fixture.redemption.getReversalReasonCode())
            .isEqualTo(CouponRedemptionReversalReason.REFUND_SUCCEEDED);
        assertThat(fixture.redemption.getReversedAt()).isEqualTo(REVERSED_AT);
    }

    @Test
    void 동일출처재처리_최초반전값을유지하는무변경성공이다() {
        RedemptionFixture fixture = redemptionFixture();
        Refund refund = succeededRefund(fixture);
        fixture.redemption.reverseForRefund(refund, REVERSED_AT);

        boolean changed = fixture.redemption.reverseForRefund(refund, REVERSED_AT.plusSeconds(1));

        assertThat(changed).isFalse();
        assertThat(fixture.redemption.getRefund()).isSameAs(refund);
        assertThat(fixture.redemption.getReversedAt()).isEqualTo(REVERSED_AT);
    }

    @Test
    void 다른출처재처리_기존반전값을보존하고거부한다() {
        RedemptionFixture fixture = redemptionFixture();
        fixture.redemption.reverseForReservationCancellation(REVERSED_AT);
        Refund refund = succeededRefund(fixture);

        assertThatThrownBy(() -> fixture.redemption.reverseForRefund(refund, REVERSED_AT.plusSeconds(1)))
            .isInstanceOf(IllegalStateException.class);
        assertThat(fixture.redemption.getRefund()).isNull();
        assertThat(fixture.redemption.getReversalReasonCode())
            .isEqualTo(CouponRedemptionReversalReason.RESERVATION_CANCELLED);
        assertThat(fixture.redemption.getReversedAt()).isEqualTo(REVERSED_AT);
    }

    @Test
    void 성공하지않은환불_유료반전출처로거부한다() {
        RedemptionFixture fixture = redemptionFixture();
        Refund refund = succeededRefund(fixture);
        when(refund.getStatus()).thenReturn(RefundStatus.FAILED);

        assertThatThrownBy(() -> fixture.redemption.reverseForRefund(refund, REVERSED_AT))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void null반전시각_상태를변경하지않고거부한다() {
        RedemptionFixture fixture = redemptionFixture();

        assertThatThrownBy(() -> fixture.redemption.reverseForReservationCancellation(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(fixture.redemption.getStatus()).isEqualTo(CouponRedemptionStatus.CONFIRMED);
        assertThat(fixture.redemption.getReversalReasonCode()).isNull();
        assertThat(fixture.redemption.getReversedAt()).isNull();
    }

    @Test
    void 생성자_가격스냅샷과다른홀드의예약을거부한다() {
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

    private RedemptionFixture redemptionFixture() {
        Coupon coupon = mock(Coupon.class);
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        Reservation reservation = mock(Reservation.class);
        CapacityHold capacityHold = mock(CapacityHold.class);
        when(snapshot.getCoupon()).thenReturn(coupon);
        when(snapshot.getCapacityHold()).thenReturn(capacityHold);
        when(snapshot.getReservationPriceSnapshotId()).thenReturn(10L);
        when(reservation.getCapacityHold()).thenReturn(capacityHold);
        when(reservation.getReservationId()).thenReturn(20L);
        return new RedemptionFixture(
            new CouponRedemption(coupon, snapshot, reservation, REDEEMED_AT),
            snapshot,
            reservation
        );
    }

    private Refund succeededRefund(RedemptionFixture fixture) {
        Refund refund = mock(Refund.class);
        Payment payment = mock(Payment.class);
        when(refund.getRefundId()).thenReturn(30L);
        when(refund.getStatus()).thenReturn(RefundStatus.SUCCEEDED);
        when(refund.getPayment()).thenReturn(payment);
        when(payment.getReservation()).thenReturn(fixture.reservation);
        when(payment.getReservationPriceSnapshot()).thenReturn(fixture.snapshot);
        return refund;
    }

    private record RedemptionFixture(
        CouponRedemption redemption,
        ReservationPriceSnapshot snapshot,
        Reservation reservation
    ) {
    }
}
