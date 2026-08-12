package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancyAction;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetOperatorReservationPaymentUseCaseTest {

    private static final Long USER_ID = 10L;
    private static final Long RESERVATION_ID = 20L;
    private static final Instant CONFIRMED_AT = Instant.parse("2026-08-12T01:00:00Z");

    @Test
    void get_whenOwnedFreeReservation_returnsConfirmedAtWithoutReadingPaymentDetails() {
        Fixture fixture = new Fixture();
        Reservation reservation = fixture.reservation();
        when(fixture.reservationService.findByIdForOperatorPaymentRead(RESERVATION_ID)).thenReturn(reservation);
        when(fixture.paymentService.findByReservationId(RESERVATION_ID)).thenReturn(Optional.empty());

        OperatorReservationPaymentInfo actual = fixture.useCase().get(USER_ID, RESERVATION_ID);

        assertThat(actual.payment()).isNull();
        assertThat(actual.refund()).isNull();
        assertThat(actual.updatedAt()).isEqualTo(CONFIRMED_AT);
        verify(fixture.authorizationService).authorizeOwnedContent(USER_ID, fixture.operator, fixture.region);
        verifyNoInteractions(
            fixture.refundService,
            fixture.paymentDiscrepancyService,
            fixture.paymentDiscrepancyActionService
        );
    }

    @Test
    void get_whenPaymentHasSingleDiscrepancy_returnsLatestStatusChangeAt() {
        Fixture fixture = new Fixture();
        Reservation reservation = fixture.reservation();
        Payment payment = mock(Payment.class);
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        Refund refund = mock(Refund.class);
        PaymentDiscrepancy discrepancy = mock(PaymentDiscrepancy.class);
        PaymentDiscrepancyAction action = mock(PaymentDiscrepancyAction.class);
        Instant paymentFinalizedAt = Instant.parse("2026-08-12T02:00:00Z");
        Instant refundRequestedAt = Instant.parse("2026-08-12T03:00:00Z");
        Instant actionAt = Instant.parse("2026-08-12T05:00:00Z");
        when(fixture.reservationService.findByIdForOperatorPaymentRead(RESERVATION_ID)).thenReturn(reservation);
        when(fixture.paymentService.findByReservationId(RESERVATION_ID)).thenReturn(Optional.of(payment));
        when(payment.getPaymentId()).thenReturn(30L);
        when(payment.getStatus()).thenReturn(PaymentStatus.APPROVED);
        when(payment.getFinalizedAt()).thenReturn(paymentFinalizedAt);
        when(payment.getReservationPriceSnapshot()).thenReturn(snapshot);
        when(snapshot.getFinalAmount()).thenReturn(17_000L);
        when(snapshot.getCurrency()).thenReturn("KRW");
        when(fixture.refundService.findByPaymentId(30L)).thenReturn(Optional.of(refund));
        when(refund.getRefundId()).thenReturn(40L);
        when(refund.getStatus()).thenReturn(RefundStatus.PROCESSING);
        when(refund.getAmount()).thenReturn(17_000L);
        when(refund.getRequestedAt()).thenReturn(refundRequestedAt);
        when(fixture.paymentDiscrepancyService.findByPaymentId(30L)).thenReturn(Optional.of(discrepancy));
        when(discrepancy.getPaymentDiscrepancyId()).thenReturn(50L);
        when(discrepancy.getStatus()).thenReturn("REFUND_REQUESTED");
        when(discrepancy.getDetectedAt()).thenReturn(paymentFinalizedAt);
        when(fixture.paymentDiscrepancyActionService.findAllByDiscrepancyId(50L)).thenReturn(List.of(action));
        when(action.getActedAt()).thenReturn(actionAt);

        OperatorReservationPaymentInfo actual = fixture.useCase().get(USER_ID, RESERVATION_ID);

        assertThat(actual.payment().paymentId()).isEqualTo(30L);
        assertThat(actual.payment().discrepancy().status()).isEqualTo("REFUND_REQUESTED");
        assertThat(actual.refund().refundId()).isEqualTo(40L);
        assertThat(actual.updatedAt()).isEqualTo(actionAt);
    }

    @Test
    void get_whenRefundIsProcessing_returnsRefundRequestedAtAsLatestRefundStatusChangeAt() {
        Fixture fixture = new Fixture();
        Reservation reservation = fixture.reservation();
        Payment payment = mock(Payment.class);
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        Refund refund = mock(Refund.class);
        Instant paymentFinalizedAt = Instant.parse("2026-08-12T02:00:00Z");
        Instant refundRequestedAt = Instant.parse("2026-08-12T03:00:00Z");
        when(fixture.reservationService.findByIdForOperatorPaymentRead(RESERVATION_ID)).thenReturn(reservation);
        when(fixture.paymentService.findByReservationId(RESERVATION_ID)).thenReturn(Optional.of(payment));
        when(payment.getPaymentId()).thenReturn(30L);
        when(payment.getStatus()).thenReturn(PaymentStatus.APPROVED);
        when(payment.getFinalizedAt()).thenReturn(paymentFinalizedAt);
        when(payment.getReservationPriceSnapshot()).thenReturn(snapshot);
        when(snapshot.getFinalAmount()).thenReturn(17_000L);
        when(snapshot.getCurrency()).thenReturn("KRW");
        when(fixture.refundService.findByPaymentId(30L)).thenReturn(Optional.of(refund));
        when(refund.getRefundId()).thenReturn(40L);
        when(refund.getStatus()).thenReturn(RefundStatus.PROCESSING);
        when(refund.getAmount()).thenReturn(17_000L);
        when(refund.getRequestedAt()).thenReturn(refundRequestedAt);
        when(fixture.paymentDiscrepancyService.findByPaymentId(30L)).thenReturn(Optional.empty());

        OperatorReservationPaymentInfo actual = fixture.useCase().get(USER_ID, RESERVATION_ID);

        assertThat(actual.updatedAt()).isEqualTo(refundRequestedAt);
        verifyNoInteractions(fixture.paymentDiscrepancyActionService);
    }

    @Test
    void get_whenFailedRefundIsRetried_returnsLatestRetryAttemptedAtAsUpdatedAt() {
        Fixture fixture = new Fixture();
        Reservation reservation = fixture.reservation();
        Payment payment = mock(Payment.class);
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        Refund refund = mock(Refund.class);
        RefundAttempt firstAttempt = mock(RefundAttempt.class);
        RefundAttempt retryAttempt = mock(RefundAttempt.class);
        Instant paymentFinalizedAt = Instant.parse("2026-08-12T02:00:00Z");
        Instant refundRequestedAt = Instant.parse("2026-08-12T03:00:00Z");
        Instant retryAttemptedAt = Instant.parse("2026-08-12T05:00:00Z");
        when(fixture.reservationService.findByIdForOperatorPaymentRead(RESERVATION_ID)).thenReturn(reservation);
        when(fixture.paymentService.findByReservationId(RESERVATION_ID)).thenReturn(Optional.of(payment));
        when(payment.getPaymentId()).thenReturn(30L);
        when(payment.getStatus()).thenReturn(PaymentStatus.APPROVED);
        when(payment.getFinalizedAt()).thenReturn(paymentFinalizedAt);
        when(payment.getReservationPriceSnapshot()).thenReturn(snapshot);
        when(snapshot.getFinalAmount()).thenReturn(17_000L);
        when(snapshot.getCurrency()).thenReturn("KRW");
        when(fixture.refundService.findByPaymentId(30L)).thenReturn(Optional.of(refund));
        when(refund.getRefundId()).thenReturn(40L);
        when(refund.getStatus()).thenReturn(RefundStatus.PROCESSING);
        when(refund.getAmount()).thenReturn(17_000L);
        when(refund.getRequestedAt()).thenReturn(refundRequestedAt);
        when(fixture.refundAttemptService.findAllByRefundId(40L)).thenReturn(
            List.of(firstAttempt, retryAttempt)
        );
        when(retryAttempt.getAttemptedAt()).thenReturn(retryAttemptedAt);
        when(fixture.paymentDiscrepancyService.findByPaymentId(30L)).thenReturn(Optional.empty());

        OperatorReservationPaymentInfo actual = fixture.useCase().get(USER_ID, RESERVATION_ID);

        assertThat(actual.updatedAt()).isEqualTo(retryAttemptedAt);
    }

    @Test
    void get_whenOperatorIsNotAuthorized_doesNotReadPayment() {
        Fixture fixture = new Fixture();
        Reservation reservation = fixture.reservation();
        when(fixture.reservationService.findByIdForOperatorPaymentRead(RESERVATION_ID))
            .thenReturn(reservation);
        when(fixture.authorizationService.authorizeOwnedContent(USER_ID, fixture.operator, fixture.region))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> fixture.useCase().get(USER_ID, RESERVATION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verifyNoInteractions(
            fixture.paymentService,
            fixture.refundService,
            fixture.paymentDiscrepancyService,
            fixture.paymentDiscrepancyActionService
        );
    }

    private static class Fixture {

        private final ReservationService reservationService = mock(ReservationService.class);
        private final OperatorAuthorizationService authorizationService = mock(OperatorAuthorizationService.class);
        private final PaymentService paymentService = mock(PaymentService.class);
        private final RefundService refundService = mock(RefundService.class);
        private final RefundAttemptService refundAttemptService = mock(RefundAttemptService.class);
        private final PaymentDiscrepancyService paymentDiscrepancyService = mock(PaymentDiscrepancyService.class);
        private final PaymentDiscrepancyActionService paymentDiscrepancyActionService = mock(
            PaymentDiscrepancyActionService.class
        );
        private final AppUser operator = mock(AppUser.class);
        private final Content content = mock(Content.class);
        private final Region region = mock(Region.class);

        private GetOperatorReservationPaymentUseCase useCase() {
            return new GetOperatorReservationPaymentUseCase(
                reservationService,
                authorizationService,
                paymentService,
                refundService,
                refundAttemptService,
                paymentDiscrepancyService,
                paymentDiscrepancyActionService
            );
        }

        private Reservation reservation() {
            Reservation reservation = mock(Reservation.class);
            ContentSession session = mock(ContentSession.class);
            when(reservation.getReservationId()).thenReturn(RESERVATION_ID);
            when(reservation.getReservationNo()).thenReturn("R20260812TEST");
            when(reservation.getConfirmedAt()).thenReturn(CONFIRMED_AT);
            when(reservation.getContentSession()).thenReturn(session);
            when(session.getSessionId()).thenReturn(21L);
            when(session.getContent()).thenReturn(content);
            when(content.getContentId()).thenReturn(22L);
            when(content.getOperator()).thenReturn(operator);
            when(content.getRegion()).thenReturn(region);
            return reservation;
        }
    }
}
