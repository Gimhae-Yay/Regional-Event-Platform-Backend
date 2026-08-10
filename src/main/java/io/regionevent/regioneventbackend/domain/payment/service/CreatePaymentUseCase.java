package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemption;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponRedemptionService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.payment.dto.CreatePaymentRequest;
import io.regionevent.regioneventbackend.domain.payment.dto.CreatePaymentResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotency;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotencyStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationConfirmationConflictException;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationPriceSnapshotService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreatePaymentUseCase {

    private static final String CURRENCY = "KRW";
    private static final String COUPON_RESERVED_REASON = "PAYMENT_CREATE";
    private static final String COUPON_USED_REASON = "ZERO_AMOUNT_RESERVATION_CONFIRMED";

    private final AppUserService appUserService;
    private final ContentService contentService;
    private final ContentSessionService contentSessionService;
    private final CapacityHoldService capacityHoldService;
    private final ReservationService reservationService;
    private final ReservationPriceSnapshotService reservationPriceSnapshotService;
    private final PaymentService paymentService;
    private final PaymentIdempotencyService paymentIdempotencyService;
    private final CouponService couponService;
    private final CouponRedemptionService couponRedemptionService;
    private final CouponStatusHistoryService couponStatusHistoryService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final PaymentCreationHasher paymentCreationHasher;

    public CreatePaymentUseCase(
        AppUserService appUserService,
        ContentService contentService,
        ContentSessionService contentSessionService,
        CapacityHoldService capacityHoldService,
        ReservationService reservationService,
        ReservationPriceSnapshotService reservationPriceSnapshotService,
        PaymentService paymentService,
        PaymentIdempotencyService paymentIdempotencyService,
        CouponService couponService,
        CouponRedemptionService couponRedemptionService,
        CouponStatusHistoryService couponStatusHistoryService,
        UserRoleAssignmentService userRoleAssignmentService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        PaymentCreationHasher paymentCreationHasher
    ) {
        this.appUserService = appUserService;
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
        this.capacityHoldService = capacityHoldService;
        this.reservationService = reservationService;
        this.reservationPriceSnapshotService = reservationPriceSnapshotService;
        this.paymentService = paymentService;
        this.paymentIdempotencyService = paymentIdempotencyService;
        this.couponService = couponService;
        this.couponRedemptionService = couponRedemptionService;
        this.couponStatusHistoryService = couponStatusHistoryService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.paymentCreationHasher = paymentCreationHasher;
    }

    @Transactional
    public CreatePaymentResponse create(
        Long userId,
        String holdIdValue,
        CreatePaymentRequest request,
        String idempotencyKey,
        UUID requestId
    ) {
        long holdId = toPositiveId(holdIdValue, ErrorCode.INVALID_TYPE);
        Long couponId = toOptionalPositiveCouponId(request);
        String validatedIdempotencyKey = validateIdempotencyKey(idempotencyKey);
        AppUser user = appUserService.findActiveUserForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        String keyHash = paymentCreationHasher.hashIdempotencyKey(validatedIdempotencyKey);
        String requestHash = paymentCreationHasher.hashRequest(holdId, couponId);
        PaymentIdempotencyService.PaymentIdempotencyAcquireResult acquired = paymentIdempotencyService
            .acquirePaymentCreate(user.getUserId(), keyHash, requestHash);
        if (!acquired.created()) {
            return existingResponse(acquired.idempotency(), requestHash);
        }
        PaymentIdempotency idempotency = acquired.idempotency();
        CapacityHold requestedHold = capacityHoldService.findOwnedHold(holdId, user);
        long baseAmount = lockReservationTarget(requestedHold);
        CapacityHold hold = capacityHoldService.findActiveOwnedHoldForUpdate(holdId, user);
        ReservationPriceSnapshot snapshot = reservationPriceSnapshotService.findByHoldIdForUpdate(holdId)
            .orElseGet(() -> createSnapshot(hold, couponId, user, baseAmount));
        validateSnapshotCoupon(snapshot, couponId);

        Payment pendingPayment = paymentService.findPendingByHoldIdForUpdate(holdId)
            .orElse(null);
        if (pendingPayment != null) {
            throw new BusinessException(ErrorCode.PAYMENT_HOLD_CONFLICT);
        }
        if (snapshot.getFinalAmount() > 0) {
            Payment payment = paymentService.create(new Payment(
                hold,
                snapshot,
                newOrderId(),
                java.time.Instant.now()
            ));
            idempotency.succeedWithPayment(payment, payment.getCreatedAt());
            return CreatePaymentResponse.fromPayment(payment);
        }
        return confirmZeroAmount(idempotency, hold, snapshot, requestId, user);
    }

    private CreatePaymentResponse existingResponse(PaymentIdempotency existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        if (existing.getStatus() == PaymentIdempotencyStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }
        if (existing.getStatus() != PaymentIdempotencyStatus.SUCCEEDED) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (existing.getPayment() != null) {
            return CreatePaymentResponse.fromPayment(existing.getPayment());
        }
        if (existing.getReservation() != null) {
            return CreatePaymentResponse.fromReservation(existing.getReservation());
        }
        throw new IllegalStateException("succeeded payment idempotency has no result");
    }

    private long lockReservationTarget(CapacityHold hold) {
        Long contentId = hold.getContentSession().getContent().getContentId();
        Long sessionId = hold.getContentSession().getSessionId();
        Long reservationPrice = contentService.findPublishedPaymentReservationPriceForUpdate(contentId)
            .orElse(null);
        if (reservationPrice == null || !contentSessionService.lockConfirmableReservationTarget(sessionId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HOLD_CONFLICT);
        }
        return reservationPrice;
    }

    private ReservationPriceSnapshot createSnapshot(
        CapacityHold hold,
        Long couponId,
        AppUser user,
        long baseAmount
    ) {
        Coupon coupon = couponId == null ? null : lockAndReserveCoupon(couponId, hold, user, baseAmount);
        long discountAmount = coupon == null ? 0 : coupon.getCouponPolicy().getDiscountAmount();
        ReservationPriceSnapshot snapshot = reservationPriceSnapshotService.create(
            new ReservationPriceSnapshot(
                hold,
                coupon,
                baseAmount,
                discountAmount,
                baseAmount - discountAmount,
                CURRENCY,
                java.time.Instant.now()
            )
        );
        return snapshot;
    }

    private Coupon lockAndReserveCoupon(
        Long couponId,
        CapacityHold hold,
        AppUser user,
        long baseAmount
    ) {
        Coupon coupon = couponService.findByCouponIdForUpdate(couponId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Content content = hold.getContentSession().getContent();
        if (coupon.getUser() == null
            || !coupon.getUser().getUserId().equals(user.getUserId())
            || coupon.getStatus() != CouponStatus.AVAILABLE
            || !coupon.getExpiresAt().isAfter(java.time.Instant.now())
            || !sameId(coupon.getCouponPolicy().getContent().getContentId(), content.getContentId())
            || !sameId(coupon.getCouponPolicy().getRegion().getRegionId(), hold.getRegion().getRegionId())
            || baseAmount < coupon.getCouponPolicy().getMinimumPaymentAmount()) {
            throw new BusinessException(ErrorCode.PAYMENT_HOLD_CONFLICT);
        }
        coupon.reserve();
        couponStatusHistoryService.create(new CouponStatusHistory(
            coupon,
            CouponStatus.AVAILABLE,
            CouponStatus.RESERVED,
            COUPON_RESERVED_REASON,
            "USER",
            java.time.Instant.now()
        ));
        return coupon;
    }

    private void validateSnapshotCoupon(ReservationPriceSnapshot snapshot, Long couponId) {
        Long snapshotCouponId = snapshot.getCoupon() == null ? null : snapshot.getCoupon().getCouponId();
        if (snapshotCouponId == null ? couponId != null : !snapshotCouponId.equals(couponId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HOLD_CONFLICT);
        }
    }

    private CreatePaymentResponse confirmZeroAmount(
        PaymentIdempotency idempotency,
        CapacityHold hold,
        ReservationPriceSnapshot snapshot,
        UUID requestId,
        AppUser user
    ) {
        try {
            CapacityHold consumedHold = capacityHoldService.consumeForPaidZeroIfConfirmable(
                hold.getHoldId(),
                hold.getUser().getUserId()
            );
            Reservation reservation = reservationService.createConfirmed(consumedHold);
            if (snapshot.getCoupon() != null) {
                Coupon coupon = couponService.findByCouponIdForUpdate(snapshot.getCoupon().getCouponId())
                    .orElseThrow(() -> new IllegalStateException("snapshot coupon does not exist"));
                coupon.use();
                couponStatusHistoryService.create(new CouponStatusHistory(
                    coupon,
                    CouponStatus.RESERVED,
                    CouponStatus.USED,
                    COUPON_USED_REASON,
                    "USER",
                    reservation.getConfirmedAt()
                ));
                couponRedemptionService.create(new CouponRedemption(
                    coupon,
                    snapshot,
                    reservation,
                    reservation.getConfirmedAt()
                ));
            }
            idempotency.succeedWithReservation(
                reservation,
                reservation.getConfirmedAt(),
                reservation.getConfirmedAt().plus(24, ChronoUnit.HOURS)
            );
            recordSuccessfulAuditEvents(
                requestId,
                new AuditEventActor(userRoleAssignmentService.findActiveVisitor(user.getUserId())),
                consumedHold,
                reservation
            );
            return CreatePaymentResponse.fromReservation(reservation);
        } catch (ReservationConfirmationConflictException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_HOLD_CONFLICT);
        }
    }

    private long toPositiveId(String value, ErrorCode typeErrorCode) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!value.matches("[0-9]+")) {
            throw new BusinessException(typeErrorCode);
        }
        try {
            long id = Long.parseLong(value);
            if (id < 1) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private Long toOptionalPositiveCouponId(CreatePaymentRequest request) {
        if (request == null || request.couponId() == null || request.couponId().isNull()) {
            return null;
        }
        JsonNode couponId = request.couponId();
        if (!couponId.isString()) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        return toPositiveId(couponId.asString(), ErrorCode.INVALID_TYPE);
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return idempotencyKey;
    }

    private String newOrderId() {
        return "ORD-" + UUID.randomUUID();
    }

    private boolean sameId(Long first, Long second) {
        return first != null && first.equals(second);
    }

    private void recordSuccessfulAuditEvents(
        UUID requestId,
        AuditEventActor actor,
        CapacityHold capacityHold,
        Reservation reservation
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            capacityHold.getRegion(),
            AuditEventTargetType.CAPACITY_HOLD,
            capacityHold.getHoldId(),
            "ACTIVE",
            "CONSUMED",
            AuditEventResult.SUCCESS,
            null,
            actor,
            capacityHold.getTerminalAt()
        ));
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            reservation.getRegion(),
            AuditEventTargetType.RESERVATION,
            reservation.getReservationId(),
            null,
            "CONFIRMED",
            AuditEventResult.SUCCESS,
            null,
            actor,
            reservation.getConfirmedAt()
        ));
    }
}
