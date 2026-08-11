package io.regionevent.regioneventbackend.domain.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

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
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentVerification;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentWebhook;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneLookupException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
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
    private final ContentService contentService;
    private final ContentSessionService contentSessionService;
    private final CapacityHoldService capacityHoldService;
    private final ReservationPriceSnapshotService reservationPriceSnapshotService;
    private final ReservationService reservationService;
    private final CouponService couponService;
    private final CouponStatusHistoryService couponStatusHistoryService;
    private final CouponRedemptionService couponRedemptionService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final TransactionTemplate transactionTemplate;

    public ReceivePortOneWebhookUseCase(
        ObjectMapper objectMapper,
        PortOneWebhookSignatureVerifier signatureVerifier,
        PortOnePaymentGateway paymentGateway,
        PaymentService paymentService,
        PaymentWebhookService paymentWebhookService,
        PaymentVerificationService paymentVerificationService,
        PaymentDiscrepancyService paymentDiscrepancyService,
        ContentService contentService,
        ContentSessionService contentSessionService,
        CapacityHoldService capacityHoldService,
        ReservationPriceSnapshotService reservationPriceSnapshotService,
        ReservationService reservationService,
        CouponService couponService,
        CouponStatusHistoryService couponStatusHistoryService,
        CouponRedemptionService couponRedemptionService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        TransactionTemplate transactionTemplate
    ) {
        this.objectMapper = objectMapper;
        this.signatureVerifier = signatureVerifier;
        this.paymentGateway = paymentGateway;
        this.paymentService = paymentService;
        this.paymentWebhookService = paymentWebhookService;
        this.paymentVerificationService = paymentVerificationService;
        this.paymentDiscrepancyService = paymentDiscrepancyService;
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
        this.capacityHoldService = capacityHoldService;
        this.reservationPriceSnapshotService = reservationPriceSnapshotService;
        this.reservationService = reservationService;
        this.couponService = couponService;
        this.couponStatusHistoryService = couponStatusHistoryService;
        this.couponRedemptionService = couponRedemptionService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
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
        if (skipProviderLookup(webhookId, rawBody, event)) {
            return;
        }
        PortOnePaymentGateway.PortOnePayment observed;
        try {
            observed = paymentGateway.findByPaymentId(event.paymentId());
        } catch (PortOneLookupException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        transactionTemplate.executeWithoutResult(status -> apply(
            webhookId,
            rawBody,
            event,
            observed,
            requestId(webhookId)
        ));
    }

    private boolean skipProviderLookup(String webhookId, String rawBody, WebhookEvent event) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            if (paymentWebhookService.existsByProviderEventId(webhookId)) {
                return true;
            }
            Payment payment = paymentService.findByOrderId(event.paymentId()).orElse(null);
            if (payment == null || !isTerminal(payment.getStatus())) {
                return false;
            }
            Payment lockedPayment = paymentService.findByOrderIdForUpdate(event.paymentId()).orElse(null);
            if (lockedPayment == null || !isTerminal(lockedPayment.getStatus())) {
                return false;
            }
            if (paymentWebhookService.existsByProviderEventId(webhookId)) {
                return true;
            }
            paymentWebhookService.create(new PaymentWebhook(
                webhookId,
                lockedPayment,
                AUTHENTICATION_RESULT,
                "ALREADY_FINALIZED",
                hash(rawBody),
                Instant.now()
            ));
            return true;
        }));
    }

    private void apply(
        String webhookId,
        String rawBody,
        WebhookEvent event,
        PortOnePaymentGateway.PortOnePayment observed,
        UUID requestId
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

        Long contentId = payment.getCapacityHold().getContentSession().getContent().getContentId();
        Long sessionId = payment.getCapacityHold().getContentSession().getSessionId();
        Long holdId = payment.getCapacityHold().getHoldId();
        Content lockedContent = contentService.findForUpdate(contentId);
        ContentSession lockedSession = contentSessionService.findForUpdate(sessionId);
        CapacityHold lockedHold = capacityHoldService.findByHoldIdForUpdate(holdId);
        ReservationPriceSnapshot snapshot = reservationPriceSnapshotService.findByHoldIdForUpdate(holdId)
            .orElseThrow(() -> new IllegalStateException("payment snapshot does not exist"));
        validateLockedPaymentContext(lockedContent, lockedSession, lockedHold, snapshot, payment);
        if (paymentWebhookService.existsByProviderEventId(webhookId)) {
            return;
        }
        if (isTerminal(payment.getStatus())) {
            paymentWebhookService.create(new PaymentWebhook(
                webhookId, payment, AUTHENTICATION_RESULT, "ALREADY_FINALIZED", hash(rawBody), now
            ));
            return;
        }

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
            approve(payment, snapshot, observed, requestId, now);
        } else if ("DECLINE".equals(decision)) {
            payment.decline(now);
            recordPaymentAudit(payment, PaymentStatus.PENDING, PaymentStatus.DECLINED, requestId, now);
            releaseCoupon(snapshot, COUPON_RELEASED_REASON, requestId, now);
        } else if ("DISCREPANT".equals(decision)) {
            String discrepancyType = discrepancyType(payment, snapshot, event, observed);
            payment.markDiscrepant(observed.transactionId(), now);
            PaymentDiscrepancy discrepancy = paymentDiscrepancyService.create(new PaymentDiscrepancy(
                payment, discrepancyType, "OPEN", now
            ));
            recordPaymentAudit(payment, PaymentStatus.PENDING, PaymentStatus.DISCREPANT, requestId, now);
            recordDiscrepancyAudit(discrepancy, requestId, now);
            releaseCoupon(snapshot, COUPON_DISCREPANCY_RELEASED_REASON, requestId, now);
        }
        paymentWebhookService.create(new PaymentWebhook(
            webhookId, payment, AUTHENTICATION_RESULT, decision, hash(rawBody), now
        ));
    }

    private void approve(
        Payment payment,
        ReservationPriceSnapshot snapshot,
        PortOnePaymentGateway.PortOnePayment observed,
        UUID requestId,
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
            recordCapacityHoldAudit(payment, requestId, now);
            recordReservationAudit(reservation, requestId, now);
            useCoupon(snapshot, reservation, requestId, now);
            paymentService.findByOrderIdForUpdate(payment.getOrderId())
                .orElseThrow(() -> new IllegalStateException("payment disappeared after capacity hold consumption"))
                .approve(reservation, observed.transactionId(), now);
            recordPaymentAudit(payment, PaymentStatus.PENDING, PaymentStatus.APPROVED, requestId, now);
        } catch (ReservationConfirmationConflictException exception) {
            payment.markDiscrepant(observed.transactionId(), now);
            PaymentDiscrepancy discrepancy = paymentDiscrepancyService.create(new PaymentDiscrepancy(
                payment, "LATE_APPROVAL", "OPEN", now
            ));
            recordPaymentAudit(payment, PaymentStatus.PENDING, PaymentStatus.DISCREPANT, requestId, now);
            recordDiscrepancyAudit(discrepancy, requestId, now);
            releaseCoupon(snapshot, COUPON_DISCREPANCY_RELEASED_REASON, requestId, now);
        }
    }

    private void validateLockedPaymentContext(
        Content content,
        ContentSession contentSession,
        CapacityHold capacityHold,
        ReservationPriceSnapshot snapshot,
        Payment payment
    ) {
        if (!contentSession.getContent().getContentId().equals(content.getContentId())
            || !capacityHold.getContentSession().getSessionId().equals(contentSession.getSessionId())
            || !snapshot.getCapacityHold().getHoldId().equals(capacityHold.getHoldId())
            || !payment.getCapacityHold().getHoldId().equals(capacityHold.getHoldId())) {
            throw new IllegalStateException("payment context changed while acquiring locks");
        }
    }

    private void useCoupon(
        ReservationPriceSnapshot snapshot,
        Reservation reservation,
        UUID requestId,
        Instant now
    ) {
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
        recordCouponAudit(coupon, CouponStatus.RESERVED, CouponStatus.USED, requestId, now);
    }

    private void releaseCoupon(
        ReservationPriceSnapshot snapshot,
        String reason,
        UUID requestId,
        Instant now
    ) {
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
        recordCouponAudit(coupon, CouponStatus.RESERVED, releasedStatus, requestId, now);
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
            || !event.storeId().equals(observed.storeId())
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
        if (!event.storeId().equals(observed.storeId())) {
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
            String storeId = requiredText(data, "storeId");
            if (!isPaymentEvent(type)) {
                return new WebhookEvent(type, storeId, null, null);
            }
            return new WebhookEvent(
                type,
                storeId,
                requiredText(data, "paymentId"),
                requiredText(data, "transactionId")
            );
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

    private UUID requestId(String webhookId) {
        return UUID.nameUUIDFromBytes(webhookId.getBytes(StandardCharsets.UTF_8));
    }

    private void recordCapacityHoldAudit(Payment payment, UUID requestId, Instant now) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId, payment.getCapacityHold().getRegion(), AuditEventTargetType.CAPACITY_HOLD,
            payment.getCapacityHold().getHoldId(), "ACTIVE", "CONSUMED", AuditEventResult.SUCCESS,
            "PORTONE_PAYMENT_APPROVED", null, now
        ));
    }

    private void recordReservationAudit(Reservation reservation, UUID requestId, Instant now) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId, reservation.getRegion(), AuditEventTargetType.RESERVATION,
            reservation.getReservationId(), null, "CONFIRMED", AuditEventResult.SUCCESS,
            "PORTONE_PAYMENT_APPROVED", null, now
        ));
    }

    private void recordPaymentAudit(
        Payment payment,
        PaymentStatus previousStatus,
        PaymentStatus nextStatus,
        UUID requestId,
        Instant now
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId, payment.getCapacityHold().getRegion(), AuditEventTargetType.PAYMENT,
            payment.getPaymentId(), previousStatus.name(), nextStatus.name(), AuditEventResult.SUCCESS,
            "PORTONE_PAYMENT_" + nextStatus.name(), null, now
        ));
    }

    private void recordCouponAudit(
        Coupon coupon,
        CouponStatus previousStatus,
        CouponStatus nextStatus,
        UUID requestId,
        Instant now
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId, coupon.getCouponPolicy().getRegion(), AuditEventTargetType.COUPON,
            coupon.getCouponId(), previousStatus.name(), nextStatus.name(), AuditEventResult.SUCCESS,
            "PORTONE_PAYMENT_COUPON_" + nextStatus.name(), null, now
        ));
    }

    private void recordDiscrepancyAudit(PaymentDiscrepancy discrepancy, UUID requestId, Instant now) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId, discrepancy.getPayment().getCapacityHold().getRegion(),
            AuditEventTargetType.PAYMENT_DISCREPANCY, discrepancy.getPaymentDiscrepancyId(), null,
            "OPEN", AuditEventResult.SUCCESS, "PORTONE_PAYMENT_DISCREPANT", null, now
        ));
    }

    private record WebhookEvent(String type, String storeId, String paymentId, String transactionId) {

        private boolean isPaymentEvent() {
            return paymentId != null;
        }
    }
}
