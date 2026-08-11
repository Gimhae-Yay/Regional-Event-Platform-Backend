package io.regionevent.regioneventbackend.domain.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemption;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponRedemptionService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentVerification;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentWebhook;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneLookupException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationConfirmationConflictException;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationPriceSnapshotService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.infra.payment.PortOneWebhookSignatureVerifier;

@Service
public class ReceivePortOneWebhookUseCase {

    private static final String AUTHENTICATION_RESULT = "VERIFIED";
    private static final String COUPON_USED_REASON = "PORTONE_PAYMENT_APPROVED";
    private static final String COUPON_RELEASED_REASON = "PORTONE_PAYMENT_DECLINED";
    private static final String COUPON_DISCREPANCY_RELEASED_REASON = "PORTONE_PAYMENT_DISCREPANT";

    private final ObjectMapper objectMapper;
    private final PortOneWebhookSignatureVerifier signatureVerifier;
    private final PortOnePaymentGateway paymentGateway;
    private final PaymentService paymentService;
    private final PaymentWebhookService paymentWebhookService;
    private final PaymentVerificationService paymentVerificationService;
    private final PaymentDiscrepancyService paymentDiscrepancyService;
    private final CapacityHoldService capacityHoldService;
    private final ReservationPriceSnapshotService reservationPriceSnapshotService;
    private final ReservationService reservationService;
    private final CouponService couponService;
    private final CouponStatusHistoryService couponStatusHistoryService;
    private final CouponRedemptionService couponRedemptionService;
    private final TransactionTemplate transactionTemplate;

    public ReceivePortOneWebhookUseCase(
        ObjectMapper objectMapper,
        PortOneWebhookSignatureVerifier signatureVerifier,
        PortOnePaymentGateway paymentGateway,
        PaymentService paymentService,
        PaymentWebhookService paymentWebhookService,
        PaymentVerificationService paymentVerificationService,
        PaymentDiscrepancyService paymentDiscrepancyService,
        CapacityHoldService capacityHoldService,
        ReservationPriceSnapshotService reservationPriceSnapshotService,
        ReservationService reservationService,
        CouponService couponService,
        CouponStatusHistoryService couponStatusHistoryService,
        CouponRedemptionService couponRedemptionService,
        TransactionTemplate transactionTemplate
    ) {
        this.objectMapper = objectMapper;
        this.signatureVerifier = signatureVerifier;
        this.paymentGateway = paymentGateway;
        this.paymentService = paymentService;
        this.paymentWebhookService = paymentWebhookService;
        this.paymentVerificationService = paymentVerificationService;
        this.paymentDiscrepancyService = paymentDiscrepancyService;
        this.capacityHoldService = capacityHoldService;
        this.reservationPriceSnapshotService = reservationPriceSnapshotService;
        this.reservationService = reservationService;
        this.couponService = couponService;
        this.couponStatusHistoryService = couponStatusHistoryService;
        this.couponRedemptionService = couponRedemptionService;
        this.transactionTemplate = transactionTemplate;
    }

    public void receive(
        String webhookId,
        String webhookTimestamp,
        String webhookSignature,
        String rawBody
    ) {
        try {
            signatureVerifier.verify(webhookId, webhookTimestamp, webhookSignature, rawBody);
        } catch (PortOneWebhookSignatureVerifier.InvalidWebhookSignatureException exception) {
            throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }

        WebhookEvent event = parse(rawBody);
        if (!event.isPaymentEvent()) {
            return;
        }
        PortOnePaymentGateway.PortOnePayment observed;
        try {
            observed = paymentGateway.findByPaymentId(event.paymentId());
        } catch (PortOneLookupException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        transactionTemplate.executeWithoutResult(status -> apply(webhookId, rawBody, event, observed));
    }

    private void apply(
        String webhookId,
        String rawBody,
        WebhookEvent event,
        PortOnePaymentGateway.PortOnePayment observed
    ) {
        Payment payment = paymentService.findByOrderIdForUpdate(event.paymentId()).orElse(null);
        if (paymentWebhookService.existsByProviderEventId(webhookId)) {
            return;
        }
        Instant now = Instant.now();
        if (payment == null) {
            paymentWebhookService.create(new PaymentWebhook(
                webhookId, null, AUTHENTICATION_RESULT, "PAYMENT_NOT_FOUND", hash(rawBody), now
            ));
            return;
        }
        if (isTerminal(payment.getStatus())) {
            paymentWebhookService.create(new PaymentWebhook(
                webhookId, payment, AUTHENTICATION_RESULT, "ALREADY_FINALIZED", hash(rawBody), now
            ));
            return;
        }

        ReservationPriceSnapshot snapshot = reservationPriceSnapshotService
            .findByHoldIdForUpdate(payment.getCapacityHold().getHoldId())
            .orElseThrow(() -> new IllegalStateException("payment snapshot does not exist"));
        String decision = decide(payment, snapshot, event, observed);
        paymentVerificationService.create(new PaymentVerification(
            payment,
            event.type(),
            observed.amount(),
            observed.currency(),
            observed.paymentId(),
            observed.status(),
            decision,
            hash(observed.paymentId() + observed.transactionId() + observed.amount() + observed.currency() + observed.status()),
            now
        ));

        if ("APPROVE".equals(decision)) {
            approve(payment, snapshot, observed, now);
        } else if ("DECLINE".equals(decision)) {
            payment.decline(now);
            releaseCoupon(snapshot, COUPON_RELEASED_REASON, now);
        } else if ("DISCREPANT".equals(decision)) {
            String discrepancyType = discrepancyType(payment, snapshot, event, observed);
            payment.markDiscrepant(observed.transactionId(), now);
            paymentDiscrepancyService.create(new PaymentDiscrepancy(payment, discrepancyType, "OPEN", now));
            releaseCoupon(snapshot, COUPON_DISCREPANCY_RELEASED_REASON, now);
        }
        paymentWebhookService.create(new PaymentWebhook(
            webhookId, payment, AUTHENTICATION_RESULT, decision, hash(rawBody), now
        ));
    }

    private void approve(
        Payment payment,
        ReservationPriceSnapshot snapshot,
        PortOnePaymentGateway.PortOnePayment observed,
        Instant now
    ) {
        try {
            Reservation reservation = reservationService.createConfirmed(
                capacityHoldService.consumeForPaidPaymentIfConfirmable(
                    payment.getCapacityHold().getHoldId(),
                    payment.getCapacityHold().getUser().getUserId(),
                    payment.getPaymentId()
                )
            );
            useCoupon(snapshot, reservation, now);
            paymentService.findByOrderIdForUpdate(payment.getOrderId())
                .orElseThrow(() -> new IllegalStateException("payment disappeared after capacity hold consumption"))
                .approve(reservation, observed.transactionId(), now);
        } catch (ReservationConfirmationConflictException exception) {
            payment.markDiscrepant(observed.transactionId(), now);
            paymentDiscrepancyService.create(new PaymentDiscrepancy(payment, "LATE_APPROVAL", "OPEN", now));
            releaseCoupon(snapshot, COUPON_DISCREPANCY_RELEASED_REASON, now);
        }
    }

    private void useCoupon(ReservationPriceSnapshot snapshot, Reservation reservation, Instant now) {
        if (snapshot.getCoupon() == null) {
            return;
        }
        Coupon coupon = couponService.findByCouponIdForUpdate(snapshot.getCoupon().getCouponId())
            .orElseThrow(() -> new IllegalStateException("snapshot coupon does not exist"));
        coupon.use();
        couponStatusHistoryService.create(new CouponStatusHistory(
            coupon, CouponStatus.RESERVED, CouponStatus.USED, COUPON_USED_REASON, "SYSTEM", now
        ));
        couponRedemptionService.create(new CouponRedemption(coupon, snapshot, reservation, now));
    }

    private void releaseCoupon(ReservationPriceSnapshot snapshot, String reason, Instant now) {
        if (snapshot.getCoupon() == null) {
            return;
        }
        Coupon coupon = couponService.findByCouponIdForUpdate(snapshot.getCoupon().getCouponId())
            .orElseThrow(() -> new IllegalStateException("snapshot coupon does not exist"));
        if (coupon.getStatus() != CouponStatus.RESERVED) {
            return;
        }
        CouponStatus releasedStatus = couponService.releaseReservedCoupon(coupon, now);
        couponStatusHistoryService.create(new CouponStatusHistory(
            coupon, CouponStatus.RESERVED, releasedStatus, reason, "SYSTEM", now
        ));
    }

    private String decide(
        Payment payment,
        ReservationPriceSnapshot snapshot,
        WebhookEvent event,
        PortOnePaymentGateway.PortOnePayment observed
    ) {
        if (!observed.isPaid()) {
            return observed.isExplicitlyDeclined() ? "DECLINE" : "PENDING";
        }
        if (payment.getStatus() == PaymentStatus.EXPIRED
            || !payment.getOrderId().equals(observed.paymentId())
            || !event.transactionId().equals(observed.transactionId())
            || snapshot.getFinalAmount() != observed.amount()
            || !snapshot.getCurrency().equals(observed.currency())) {
            return "DISCREPANT";
        }
        return "APPROVE";
    }

    private String discrepancyType(
        Payment payment,
        ReservationPriceSnapshot snapshot,
        WebhookEvent event,
        PortOnePaymentGateway.PortOnePayment observed
    ) {
        if (payment.getStatus() == PaymentStatus.EXPIRED) {
            return "LATE_APPROVAL";
        }
        if (!payment.getOrderId().equals(observed.paymentId())) {
            return "ORDER_MISMATCH";
        }
        if (!event.transactionId().equals(observed.transactionId())) {
            return "TARGET_MISMATCH";
        }
        return snapshot.getFinalAmount() != observed.amount()
            || !snapshot.getCurrency().equals(observed.currency())
            ? "AMOUNT_MISMATCH"
            : "TARGET_MISMATCH";
    }

    private WebhookEvent parse(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String type = requiredText(root, "type");
            requiredText(root, "timestamp");
            JsonNode data = root.get("data");
            if (data == null || !data.isObject()) {
                throw new IllegalArgumentException();
            }
            requiredText(data, "storeId");
            if (!isPaymentEvent(type)) {
                return new WebhookEvent(type, null, null);
            }
            return new WebhookEvent(type, requiredText(data, "paymentId"), requiredText(data, "transactionId"));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_JSON);
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual() || value.asString().isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.asString();
    }

    private boolean isPaymentEvent(String type) {
        return switch (type) {
            case "Transaction.Ready", "Transaction.Paid", "Transaction.VirtualAccountIssued",
                "Transaction.PartialCancelled", "Transaction.Cancelled", "Transaction.Failed",
                "Transaction.PayPending", "Transaction.CancelPending", "Transaction.DisputeCreated",
                "Transaction.DisputeResolved" -> true;
            default -> false;
        };
    }

    private boolean isTerminal(PaymentStatus status) {
        return status == PaymentStatus.APPROVED
            || status == PaymentStatus.DECLINED
            || status == PaymentStatus.CANCELLED
            || status == PaymentStatus.DISCREPANT;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record WebhookEvent(String type, String paymentId, String transactionId) {

        private boolean isPaymentEvent() {
            return paymentId != null;
        }
    }
}
