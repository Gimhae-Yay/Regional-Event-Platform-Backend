package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancyAction;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;

@Service
public class GetOperatorReservationPaymentUseCase {

    private final ReservationService reservationService;
    private final OperatorAuthorizationService operatorAuthorizationService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final RefundAttemptService refundAttemptService;
    private final PaymentDiscrepancyService paymentDiscrepancyService;
    private final PaymentDiscrepancyActionService paymentDiscrepancyActionService;

    public GetOperatorReservationPaymentUseCase(
        ReservationService reservationService,
        OperatorAuthorizationService operatorAuthorizationService,
        PaymentService paymentService,
        RefundService refundService,
        RefundAttemptService refundAttemptService,
        PaymentDiscrepancyService paymentDiscrepancyService,
        PaymentDiscrepancyActionService paymentDiscrepancyActionService
    ) {
        this.reservationService = reservationService;
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.refundAttemptService = refundAttemptService;
        this.paymentDiscrepancyService = paymentDiscrepancyService;
        this.paymentDiscrepancyActionService = paymentDiscrepancyActionService;
    }

    @Transactional(readOnly = true)
    public OperatorReservationPaymentInfo get(Long userId, Long reservationId) {
        Reservation reservation = reservationService.findByIdForOperatorPaymentRead(reservationId);
        ContentSession session = reservation.getContentSession();
        Content content = session.getContent();
        operatorAuthorizationService.authorizeOwnedContent(userId, content.getOperator(), content.getRegion());

        Payment payment = paymentService.findByReservationId(reservationId).orElse(null);
        if (payment == null) {
            return new OperatorReservationPaymentInfo(
                reservation.getReservationId(),
                reservation.getReservationNo(),
                content.getContentId(),
                session.getSessionId(),
                null,
                null,
                reservation.getConfirmedAt()
            );
        }

        Refund refund = refundService.findByPaymentId(payment.getPaymentId()).orElse(null);
        PaymentDiscrepancy discrepancy = paymentDiscrepancyService
            .findByPaymentId(payment.getPaymentId())
            .orElse(null);
        List<Instant> updatedAts = new ArrayList<>();
        updatedAts.add(payment.getFinalizedAt() == null ? payment.getCreatedAt() : payment.getFinalizedAt());
        updatedAts.add(latestRefundUpdatedAt(refund));
        updatedAts.add(latestDiscrepancyUpdatedAt(discrepancy));

        ReservationPriceSnapshot snapshot = payment.getReservationPriceSnapshot();
        return new OperatorReservationPaymentInfo(
            reservation.getReservationId(),
            reservation.getReservationNo(),
            content.getContentId(),
            session.getSessionId(),
            new OperatorReservationPaymentInfo.PaymentInfo(
                payment.getPaymentId(),
                payment.getStatus(),
                snapshot.getFinalAmount(),
                snapshot.getCurrency(),
                toDiscrepancyInfo(discrepancy)
            ),
            toRefundInfo(refund),
            latest(updatedAts)
        );
    }

    private Instant latestRefundUpdatedAt(Refund refund) {
        if (refund == null) {
            return null;
        }

        Instant updatedAt = refund.getRequestedAt();
        if (refund.getCompletedAt() != null) {
            updatedAt = latest(List.of(updatedAt, refund.getCompletedAt()));
        }
        for (RefundAttempt attempt : refundAttemptService.findAllByRefundId(refund.getRefundId())) {
            updatedAt = latest(List.of(updatedAt, attempt.getAttemptedAt()));
        }
        return updatedAt;
    }

    private Instant latestDiscrepancyUpdatedAt(PaymentDiscrepancy discrepancy) {
        if (discrepancy == null) {
            return null;
        }

        Instant updatedAt = discrepancy.getDetectedAt();
        for (PaymentDiscrepancyAction action : paymentDiscrepancyActionService
            .findAllByDiscrepancyId(discrepancy.getPaymentDiscrepancyId())) {
            updatedAt = latest(List.of(updatedAt, action.getActedAt()));
        }
        return updatedAt;
    }

    private OperatorReservationPaymentInfo.DiscrepancyInfo toDiscrepancyInfo(
        PaymentDiscrepancy discrepancy
    ) {
        if (discrepancy == null) {
            return null;
        }
        return new OperatorReservationPaymentInfo.DiscrepancyInfo(
            discrepancy.getPaymentDiscrepancyId(),
            discrepancy.getStatus()
        );
    }

    private OperatorReservationPaymentInfo.RefundInfo toRefundInfo(Refund refund) {
        if (refund == null) {
            return null;
        }
        return new OperatorReservationPaymentInfo.RefundInfo(
            refund.getRefundId(),
            refund.getStatus(),
            refund.getAmount(),
            refund.getRequestedAt(),
            refund.getCompletedAt()
        );
    }

    private Instant latest(List<Instant> values) {
        return values.stream()
            .filter(value -> value != null)
            .max(Instant::compareTo)
            .orElseThrow(() -> new IllegalStateException("payment status updatedAt must exist"));
    }
}
