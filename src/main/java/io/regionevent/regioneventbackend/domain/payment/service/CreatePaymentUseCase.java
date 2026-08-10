package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemption;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRedemptionRepository;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.payment.dto.CreatePaymentRequest;
import io.regionevent.regioneventbackend.domain.payment.dto.CreatePaymentResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotency;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotencyStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentIdempotencyRepository;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationPriceSnapshotRepository;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationConfirmationConflictException;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
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
    private final ReservationPriceSnapshotRepository reservationPriceSnapshotRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentIdempotencyRepository paymentIdempotencyRepository;
    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final CouponStatusHistoryService couponStatusHistoryService;
    private final PaymentCreationHasher paymentCreationHasher;

    public CreatePaymentUseCase(
        AppUserService appUserService,
        ContentService contentService,
        ContentSessionService contentSessionService,
        CapacityHoldService capacityHoldService,
        ReservationService reservationService,
        ReservationPriceSnapshotRepository reservationPriceSnapshotRepository,
        PaymentRepository paymentRepository,
        PaymentIdempotencyRepository paymentIdempotencyRepository,
        CouponRepository couponRepository,
        CouponRedemptionRepository couponRedemptionRepository,
        CouponStatusHistoryService couponStatusHistoryService,
        PaymentCreationHasher paymentCreationHasher
    ) {
        this.appUserService = appUserService;
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
        this.capacityHoldService = capacityHoldService;
        this.reservationService = reservationService;
        this.reservationPriceSnapshotRepository = reservationPriceSnapshotRepository;
        this.paymentRepository = paymentRepository;
        this.paymentIdempotencyRepository = paymentIdempotencyRepository;
        this.couponRepository = couponRepository;
        this.couponRedemptionRepository = couponRedemptionRepository;
        this.couponStatusHistoryService = couponStatusHistoryService;
        this.paymentCreationHasher = paymentCreationHasher;
    }

    @Transactional
    public CreatePaymentResponse create(
        Long userId,
        String holdIdValue,
        CreatePaymentRequest request,
        String idempotencyKey
    ) {
        long holdId = toPositiveId(holdIdValue, ErrorCode.INVALID_TYPE);
        Long couponId = toOptionalPositiveCouponId(request);
        String validatedIdempotencyKey = validateIdempotencyKey(idempotencyKey);
        AppUser user = appUserService.findActiveUserForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        String keyHash = paymentCreationHasher.hashIdempotencyKey(validatedIdempotencyKey);
        String requestHash = paymentCreationHasher.hashRequest(holdId, couponId);
        PaymentIdempotency existing = paymentIdempotencyRepository
            .findByActorUserIdAndOperationAndIdempotencyKeyHashForUpdate(
                user.getUserId(),
                io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotencyOperation.PAYMENT_CREATE,
                keyHash
            )
            .orElse(null);
        if (existing != null) {
            return existingResponse(existing, requestHash);
        }

        PaymentIdempotency idempotency = paymentIdempotencyRepository.saveAndFlush(
            new PaymentIdempotency(user.getUserId(), keyHash, requestHash)
        );
        CapacityHold requestedHold = capacityHoldService.findOwnedHold(holdId, user);
        lockReservationTarget(requestedHold);
        CapacityHold hold = capacityHoldService.findActiveOwnedHoldForUpdate(holdId, user);
        if (!hold.getExpiresAt().isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.PAYMENT_HOLD_CONFLICT);
        }
        ReservationPriceSnapshot snapshot = reservationPriceSnapshotRepository.findByHoldIdForUpdate(holdId)
            .orElseGet(() -> createSnapshot(hold, couponId, user));
        validateSnapshotCoupon(snapshot, couponId);

        Payment pendingPayment = paymentRepository.findByHoldIdAndStatusForUpdate(holdId, PaymentStatus.PENDING)
            .orElse(null);
        if (pendingPayment != null) {
            throw new BusinessException(ErrorCode.PAYMENT_HOLD_CONFLICT);
        }
        if (snapshot.getFinalAmount() > 0) {
            Payment payment = paymentRepository.saveAndFlush(new Payment(
                hold,
                snapshot,
                newOrderId(),
                Instant.now()
            ));
            idempotency.succeedWithPayment(payment, payment.getCreatedAt(), payment.getCreatedAt().plus(24, ChronoUnit.HOURS));
            return CreatePaymentResponse.fromPayment(payment);
        }
        return confirmZeroAmount(idempotency, hold, snapshot);
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

    private void lockReservationTarget(CapacityHold hold) {
        Long contentId = hold.getContentSession().getContent().getContentId();
        Long sessionId = hold.getContentSession().getSessionId();
        if (!contentService.lockPublishedPaymentTarget(contentId)
            || !contentSessionService.lockConfirmableReservationTarget(sessionId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HOLD_CONFLICT);
        }
    }

    private ReservationPriceSnapshot createSnapshot(CapacityHold hold, Long couponId, AppUser user) {
        Coupon coupon = couponId == null ? null : lockAndReserveCoupon(couponId, hold, user);
        long baseAmount = hold.getContentSession().getContent().getReservationPrice();
        long discountAmount = coupon == null ? 0 : coupon.getCouponPolicy().getDiscountAmount();
        ReservationPriceSnapshot snapshot = reservationPriceSnapshotRepository.saveAndFlush(
            new ReservationPriceSnapshot(
                hold,
                coupon,
                baseAmount,
                discountAmount,
                baseAmount - discountAmount,
                CURRENCY,
                Instant.now()
            )
        );
        return snapshot;
    }

    private Coupon lockAndReserveCoupon(Long couponId, CapacityHold hold, AppUser user) {
        Coupon coupon = couponRepository.findByCouponIdForUpdate(couponId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Content content = hold.getContentSession().getContent();
        if (coupon.getUser() == null
            || !coupon.getUser().getUserId().equals(user.getUserId())
            || coupon.getStatus() != CouponStatus.AVAILABLE
            || !coupon.getExpiresAt().isAfter(Instant.now())
            || !sameId(coupon.getCouponPolicy().getContent().getContentId(), content.getContentId())
            || !sameId(coupon.getCouponPolicy().getRegion().getRegionId(), hold.getRegion().getRegionId())
            || content.getReservationPrice() < coupon.getCouponPolicy().getMinimumPaymentAmount()) {
            throw new BusinessException(ErrorCode.PAYMENT_HOLD_CONFLICT);
        }
        coupon.reserve();
        couponStatusHistoryService.create(new CouponStatusHistory(
            coupon,
            CouponStatus.AVAILABLE,
            CouponStatus.RESERVED,
            COUPON_RESERVED_REASON,
            "USER",
            Instant.now()
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
        ReservationPriceSnapshot snapshot
    ) {
        try {
            CapacityHold consumedHold = capacityHoldService.consumeForPaidZeroIfConfirmable(
                hold.getHoldId(),
                hold.getUser().getUserId()
            );
            Reservation reservation = reservationService.createConfirmed(consumedHold);
            if (snapshot.getCoupon() != null) {
                Coupon coupon = couponRepository.findByCouponIdForUpdate(snapshot.getCoupon().getCouponId())
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
                couponRedemptionRepository.saveAndFlush(new CouponRedemption(
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
}
