package io.regionevent.regioneventbackend.domain.coupon.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuance;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetMyCouponUseCase {

    private final AppUserService appUserService;
    private final CouponIssuanceService couponIssuanceService;

    public GetMyCouponUseCase(
        AppUserService appUserService,
        CouponIssuanceService couponIssuanceService
    ) {
        this.appUserService = appUserService;
        this.couponIssuanceService = couponIssuanceService;
    }

    @Transactional(readOnly = true)
    public GetMyCouponResult find(Long userId, Long couponId) {
        AppUser user = appUserService.findActiveUser(userId);
        CouponIssuance couponIssuance = couponIssuanceService.findByCouponId(couponId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        validateOwnership(couponIssuance.getCoupon(), user);
        return GetMyCouponResult.from(couponIssuance);
    }

    private void validateOwnership(Coupon coupon, AppUser user) {
        if (coupon.getUser() == null || !coupon.getUser().getUserId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
