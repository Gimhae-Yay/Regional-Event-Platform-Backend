package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetMyAvailableCouponsUseCase {

    private final AppUserService appUserService;
    private final CapacityHoldService capacityHoldService;
    private final CouponService couponService;
    private final Clock clock;

    public GetMyAvailableCouponsUseCase(
        AppUserService appUserService,
        CapacityHoldService capacityHoldService,
        CouponService couponService,
        Clock clock
    ) {
        this.appUserService = appUserService;
        this.capacityHoldService = capacityHoldService;
        this.couponService = couponService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GetMyAvailableCouponsResult findAll(Long userId, Long holdId) {
        AppUser user = appUserService.findActiveUser(userId);
        CapacityHold hold = capacityHoldService.findOwnedHold(holdId, user);
        Instant evaluatedAt = clock.instant();
        validateAvailableHold(hold, evaluatedAt);

        long baseAmount = Math.multiplyExact(
            hold.getContentSession().getContent().getReservationPrice(),
            hold.getQuantity()
        );
        return new GetMyAvailableCouponsResult(
            holdId,
            evaluatedAt,
            couponService.findAllByUserId(userId, CouponStatus.AVAILABLE).stream()
                .filter(coupon -> isApplicable(coupon, hold, baseAmount, evaluatedAt))
                .map(coupon -> toAvailableCoupon(coupon, baseAmount))
                .toList()
        );
    }

    private void validateAvailableHold(CapacityHold hold, Instant evaluatedAt) {
        if (hold.getStatus() != CapacityHoldStatus.ACTIVE || !hold.getExpiresAt().isAfter(evaluatedAt)) {
            throw new BusinessException(ErrorCode.COUPON_AVAILABILITY_CONFLICT);
        }
    }

    private boolean isApplicable(
        Coupon coupon,
        CapacityHold hold,
        long baseAmount,
        Instant evaluatedAt
    ) {
        return coupon.getExpiresAt().isAfter(evaluatedAt)
            && coupon.getCouponPolicy().getContent().getContentId().equals(
                hold.getContentSession().getContent().getContentId()
            )
            && coupon.getCouponPolicy().getRegion().getRegionId().equals(hold.getRegion().getRegionId())
            && baseAmount >= coupon.getCouponPolicy().getMinimumPaymentAmount();
    }

    private GetMyAvailableCouponsResult.AvailableCoupon toAvailableCoupon(
        Coupon coupon,
        long baseAmount
    ) {
        long discountAmount = Math.min(coupon.getCouponPolicy().getDiscountAmount(), baseAmount);
        return new GetMyAvailableCouponsResult.AvailableCoupon(
            CouponSummary.from(coupon),
            baseAmount,
            discountAmount,
            baseAmount - discountAmount
        );
    }
}
