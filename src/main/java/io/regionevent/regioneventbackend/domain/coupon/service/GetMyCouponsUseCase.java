package io.regionevent.regioneventbackend.domain.coupon.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;

@Service
public class GetMyCouponsUseCase {

    private final AppUserService appUserService;
    private final CouponService couponService;

    public GetMyCouponsUseCase(
        AppUserService appUserService,
        CouponService couponService
    ) {
        this.appUserService = appUserService;
        this.couponService = couponService;
    }

    @Transactional(readOnly = true)
    public List<CouponSummary> findAll(
        Long userId,
        CouponStatus status
    ) {
        appUserService.findActiveUser(userId);
        return couponService.findAllByUserId(userId, status).stream()
            .map(CouponSummary::from)
            .toList();
    }
}
