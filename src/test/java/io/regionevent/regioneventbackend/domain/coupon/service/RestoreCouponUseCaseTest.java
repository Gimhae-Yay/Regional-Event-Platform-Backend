package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemption;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemptionReversalReason;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.service.PaymentService;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationPriceSnapshotService;

class RestoreCouponUseCaseTest {

    private static final UUID REQUEST_ID = UUID.fromString("559ed540-763c-4f90-9cdb-01a827d8a271");
    private static final Instant RESTORED_AT = Instant.parse("2026-08-15T03:00:00Z");

    @Test
    void 유료환불복구_실제환불출처와이력감사를함께기록한다() {
        Fixture fixture = new Fixture();
        Refund refund = fixture.succeededRefund();

        boolean changed = fixture.useCase.restoreForRefund(refund, REQUEST_ID, null);

        assertThat(changed).isTrue();
        assertThat(fixture.redemption.getRefund()).isSameAs(refund);
        assertThat(fixture.redemption.getReversalReasonCode())
            .isEqualTo(CouponRedemptionReversalReason.REFUND_SUCCEEDED);
        assertThat(fixture.redemption.getReversedAt()).isEqualTo(RESTORED_AT);
        verify(fixture.couponService).restoreUsedCoupon(fixture.coupon, RESTORED_AT);
        ArgumentCaptor<CouponStatusHistory> historyCaptor = ArgumentCaptor.forClass(CouponStatusHistory.class);
        verify(fixture.couponStatusHistoryService).create(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getReasonCode()).isEqualTo("REFUND_SUCCEEDED");
        assertThat(historyCaptor.getValue().getActorKind()).isEqualTo("SYSTEM");
        verify(fixture.recordAuditEventUseCase).record(any());
    }

    @Test
    void 무료예약취소복구_환불행없이예약취소출처를기록한다() {
        Fixture fixture = new Fixture();
        when(fixture.reservationPriceSnapshotService.findByHoldIdForUpdate(11L))
            .thenReturn(Optional.of(fixture.snapshot));
        when(fixture.snapshot.getFinalAmount()).thenReturn(0L);
        when(fixture.paymentService.findByReservationId(21L)).thenReturn(Optional.empty());

        boolean changed = fixture.useCase.restoreForReservationCancellation(
            fixture.reservation,
            REQUEST_ID,
            null
        );

        assertThat(changed).isTrue();
        assertThat(fixture.redemption.getRefund()).isNull();
        assertThat(fixture.redemption.getReversalReasonCode())
            .isEqualTo(CouponRedemptionReversalReason.RESERVATION_CANCELLED);
        assertThat(fixture.redemption.getReversedAt()).isEqualTo(RESTORED_AT);
    }

    @Test
    void 동일환불재처리_최초기록을유지하고추가복구를생성하지않는다() {
        Fixture fixture = new Fixture();
        Refund refund = fixture.succeededRefund();
        fixture.redemption.reverseForRefund(refund, RESTORED_AT.minusSeconds(1));

        boolean changed = fixture.useCase.restoreForRefund(refund, REQUEST_ID, null);

        assertThat(changed).isFalse();
        assertThat(fixture.redemption.getReversedAt()).isEqualTo(RESTORED_AT.minusSeconds(1));
        verify(fixture.couponService, never()).restoreUsedCoupon(any(), any());
        verify(fixture.couponStatusHistoryService, never()).create(any());
        verify(fixture.recordAuditEventUseCase, never()).record(any());
    }

    @Test
    void 다른출처재처리_기존기록을보존하고내부정합성실패로거부한다() {
        Fixture fixture = new Fixture();
        Refund refund = fixture.succeededRefund();
        fixture.redemption.reverseForReservationCancellation(RESTORED_AT.minusSeconds(1));

        assertThatThrownBy(() -> fixture.useCase.restoreForRefund(refund, REQUEST_ID, null))
            .isInstanceOf(IllegalStateException.class);
        assertThat(fixture.redemption.getRefund()).isNull();
        assertThat(fixture.redemption.getReversalReasonCode())
            .isEqualTo(CouponRedemptionReversalReason.RESERVATION_CANCELLED);
        assertThat(fixture.redemption.getReversedAt()).isEqualTo(RESTORED_AT.minusSeconds(1));
        verify(fixture.couponService, never()).restoreUsedCoupon(any(), any());
    }

    @Test
    void 무료예약취소복구_결제행이있으면거부한다() {
        Fixture fixture = new Fixture();
        when(fixture.reservationPriceSnapshotService.findByHoldIdForUpdate(11L))
            .thenReturn(Optional.of(fixture.snapshot));
        when(fixture.snapshot.getFinalAmount()).thenReturn(0L);
        when(fixture.paymentService.findByReservationId(21L)).thenReturn(Optional.of(mock(Payment.class)));

        assertThatThrownBy(() -> fixture.useCase.restoreForReservationCancellation(
            fixture.reservation,
            REQUEST_ID,
            null
        )).isInstanceOf(IllegalStateException.class);
    }

    private static class Fixture {

        private final ReservationPriceSnapshotService reservationPriceSnapshotService = mock(
            ReservationPriceSnapshotService.class
        );
        private final PaymentService paymentService = mock(PaymentService.class);
        private final CouponRedemptionService couponRedemptionService = mock(CouponRedemptionService.class);
        private final CouponService couponService = mock(CouponService.class);
        private final CouponStatusHistoryService couponStatusHistoryService = mock(CouponStatusHistoryService.class);
        private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
        private final CapacityHold capacityHold = mock(CapacityHold.class);
        private final ContentSession contentSession = mock(ContentSession.class);
        private final Reservation reservation = mock(Reservation.class);
        private final ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        private final Coupon coupon = mock(Coupon.class);
        private final CouponRedemption redemption;
        private final RestoreCouponUseCase useCase;

        private Fixture() {
            when(capacityHold.getHoldId()).thenReturn(11L);
            when(reservation.getReservationId()).thenReturn(21L);
            when(reservation.getCapacityHold()).thenReturn(capacityHold);
            when(reservation.getContentSession()).thenReturn(contentSession);
            when(reservation.getStatus()).thenReturn(ReservationStatus.CANCELLED);
            when(reservation.getCancelledAt()).thenReturn(Instant.parse("2026-08-15T01:00:00Z"));
            when(contentSession.getStartsAt()).thenReturn(Instant.parse("2026-08-15T02:00:00Z"));
            when(snapshot.getReservationPriceSnapshotId()).thenReturn(31L);
            when(snapshot.getCapacityHold()).thenReturn(capacityHold);
            when(snapshot.getCoupon()).thenReturn(coupon);
            when(coupon.getCouponId()).thenReturn(41L);
            when(coupon.getStatus()).thenReturn(CouponStatus.USED);
            redemption = new CouponRedemption(
                coupon,
                snapshot,
                reservation,
                Instant.parse("2026-08-14T00:00:00Z")
            );
            when(couponRedemptionService.findByReservationPriceSnapshotIdForUpdate(31L))
                .thenReturn(Optional.of(redemption));
            when(couponService.findByCouponIdForUpdate(41L)).thenReturn(Optional.of(coupon));
            when(couponService.findCurrentDatabaseTime()).thenReturn(RESTORED_AT);
            when(couponService.restoreUsedCoupon(coupon, RESTORED_AT)).thenReturn(CouponStatus.AVAILABLE);
            useCase = new RestoreCouponUseCase(
                reservationPriceSnapshotService,
                paymentService,
                couponRedemptionService,
                couponService,
                couponStatusHistoryService,
                recordAuditEventUseCase
            );
        }

        private Refund succeededRefund() {
            Refund refund = mock(Refund.class);
            Payment payment = mock(Payment.class);
            when(refund.getRefundId()).thenReturn(51L);
            when(refund.getStatus()).thenReturn(RefundStatus.SUCCEEDED);
            when(refund.getPayment()).thenReturn(payment);
            when(payment.getReservation()).thenReturn(reservation);
            when(payment.getReservationPriceSnapshot()).thenReturn(snapshot);
            return refund;
        }
    }
}
