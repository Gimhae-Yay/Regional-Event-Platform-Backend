package io.regionevent.regioneventbackend.domain.payment.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;

public record CreatePaymentResponse(
    boolean requiresPayment,
    PaymentResponse payment,
    ReservationResponse reservation
) {

    public static CreatePaymentResponse fromPayment(Payment payment) {
        ReservationPriceSnapshot snapshot = payment.getReservationPriceSnapshot();
        return new CreatePaymentResponse(
            true,
            new PaymentResponse(
                payment.getPaymentId().toString(),
                payment.getCapacityHold().getHoldId().toString(),
                payment.getOrderId(),
                PaymentStatus.PENDING.name(),
                new AmountResponse(
                    snapshot.getBaseAmount(),
                    snapshot.getDiscountAmount(),
                    snapshot.getFinalAmount(),
                    snapshot.getCurrency()
                ),
                payment.getCreatedAt()
            ),
            null
        );
    }

    public static CreatePaymentResponse fromReservation(Reservation reservation) {
        return new CreatePaymentResponse(
            false,
            null,
            new ReservationResponse(
                reservation.getReservationId().toString(),
                reservation.getReservationNo(),
                reservation.getCapacityHold().getHoldId().toString(),
                ReservationStatus.CONFIRMED.name(),
                reservation.getConfirmedAt()
            )
        );
    }

    public record PaymentResponse(
        String paymentId,
        String holdId,
        String orderId,
        String status,
        AmountResponse amount,
        Instant createdAt
    ) {
    }

    public record AmountResponse(
        long baseAmount,
        long discountAmount,
        long finalAmount,
        String currency
    ) {
    }

    public record ReservationResponse(
        String reservationId,
        String reservationNo,
        String holdId,
        String status,
        Instant confirmedAt
    ) {
    }
}
