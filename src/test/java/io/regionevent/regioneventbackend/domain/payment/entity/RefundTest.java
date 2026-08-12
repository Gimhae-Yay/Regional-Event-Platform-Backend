package io.regionevent.regioneventbackend.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;

class RefundTest {

    @Test
    void retry_failed_환불을_처리_중으로_되돌린다() {
        Payment payment = mock(Payment.class);
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        when(payment.getReservationPriceSnapshot()).thenReturn(snapshot);
        when(snapshot.getFinalAmount()).thenReturn(10_000L);
        Refund refund = new Refund(payment, 10_000L, Instant.parse("2026-08-12T00:00:00Z"));
        refund.startProcessing();
        refund.fail(Instant.parse("2026-08-12T00:01:00Z"));

        refund.retry();

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(refund.getCompletedAt()).isNull();
    }

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

    @Test
    void 처리중_환불_성공_상태로_전이한다() {
        Payment payment = mock(Payment.class);
        ReservationPriceSnapshot reservationPriceSnapshot = mock(ReservationPriceSnapshot.class);
        when(payment.getReservationPriceSnapshot()).thenReturn(reservationPriceSnapshot);
        when(reservationPriceSnapshot.getFinalAmount()).thenReturn(7_000L);
        Refund refund = new Refund(payment, 7_000L, REQUESTED_AT);
        Instant completedAt = Instant.parse("2026-08-10T00:01:00Z");

        refund.startProcessing();
        refund.succeed(completedAt);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(refund.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void 요청_상태에서는_환불_성공으로_전이할_수_없다() {
        Payment payment = mock(Payment.class);
        ReservationPriceSnapshot reservationPriceSnapshot = mock(ReservationPriceSnapshot.class);
        when(payment.getReservationPriceSnapshot()).thenReturn(reservationPriceSnapshot);
        when(reservationPriceSnapshot.getFinalAmount()).thenReturn(7_000L);
        Refund refund = new Refund(payment, 7_000L, REQUESTED_AT);

        assertThatThrownBy(() -> refund.succeed(REQUESTED_AT.plusSeconds(1)))
            .isInstanceOf(IllegalStateException.class);
    }
}
