package io.regionevent.regioneventbackend.domain.payment.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;

class GetMyPaymentResponseTest {

    @Test
    void from_whenPendingPayment_mapsSnapshotAndKeepsTerminalFieldsNull() {
        Payment payment = payment(PaymentStatus.PENDING, null);

        GetMyPaymentResponse response = GetMyPaymentResponse.from(payment);

        assertThat(response.paymentId()).isEqualTo("11");
        assertThat(response.orderId()).isEqualTo("ORDER-11");
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.amount()).isEqualTo(new GetMyPaymentResponse.AmountResponse(20_000, 3_000, 17_000, "KRW"));
        assertThat(response.reservationId()).isNull();
        assertThat(response.finalizedAt()).isNull();
    }

    @Test
    void from_whenExpiredPayment_mapsFinalizedAtAndKeepsReservationIdNull() {
        Instant finalizedAt = Instant.parse("2026-08-11T01:00:00Z");
        Payment payment = payment(PaymentStatus.EXPIRED, finalizedAt);

        GetMyPaymentResponse response = GetMyPaymentResponse.from(payment);

        assertThat(response.status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(response.reservationId()).isNull();
        assertThat(response.finalizedAt()).isEqualTo(finalizedAt);
    }

    private Payment payment(PaymentStatus status, Instant finalizedAt) {
        CapacityHold capacityHold = mock(CapacityHold.class);
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        Payment payment = mock(Payment.class);
        when(capacityHold.getHoldId()).thenReturn(10L);
        when(snapshot.getBaseAmount()).thenReturn(20_000L);
        when(snapshot.getDiscountAmount()).thenReturn(3_000L);
        when(snapshot.getFinalAmount()).thenReturn(17_000L);
        when(snapshot.getCurrency()).thenReturn("KRW");
        when(payment.getPaymentId()).thenReturn(11L);
        when(payment.getCapacityHold()).thenReturn(capacityHold);
        when(payment.getReservationPriceSnapshot()).thenReturn(snapshot);
        when(payment.getOrderId()).thenReturn("ORDER-11");
        when(payment.getStatus()).thenReturn(status);
        when(payment.getCreatedAt()).thenReturn(Instant.parse("2026-08-11T00:00:00Z"));
        when(payment.getFinalizedAt()).thenReturn(finalizedAt);
        return payment;
    }
}
