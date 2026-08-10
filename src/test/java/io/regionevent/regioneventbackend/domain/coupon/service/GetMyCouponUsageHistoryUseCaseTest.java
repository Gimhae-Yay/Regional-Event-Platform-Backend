package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuance;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetMyCouponUsageHistoryUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long COUPON_ID = 200L;

    private final AppUserService appUserService = mock(AppUserService.class);
    private final CouponIssuanceService couponIssuanceService = mock(CouponIssuanceService.class);
    private final CouponRedemptionService couponRedemptionService = mock(CouponRedemptionService.class);
    private final GetMyCouponUsageHistoryUseCase getMyCouponUsageHistoryUseCase =
        new GetMyCouponUsageHistoryUseCase(
            appUserService,
            couponIssuanceService,
            couponRedemptionService
        );

    @Test
    void 내_쿠폰_사용_이력이_없으면_빈_목록을_반환한다() {
        AppUser user = user(USER_ID);
        Coupon coupon = coupon(user);
        CouponIssuance couponIssuance = mock(CouponIssuance.class);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(couponIssuanceService.findByCouponId(COUPON_ID)).thenReturn(Optional.of(couponIssuance));
        when(couponIssuance.getCoupon()).thenReturn(coupon);
        when(couponRedemptionService.findAllByCouponId(COUPON_ID)).thenReturn(List.of());

        GetMyCouponUsageHistoryResult result = getMyCouponUsageHistoryUseCase.find(USER_ID, COUPON_ID);

        assertThat(result.couponId()).isEqualTo(COUPON_ID);
        assertThat(result.usageHistory()).isEmpty();
    }

    @Test
    void 타인_쿠폰의_사용_이력은_FORBIDDEN으로_처리한다() {
        AppUser user = user(USER_ID);
        Coupon coupon = coupon(user(101L));
        CouponIssuance couponIssuance = mock(CouponIssuance.class);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(couponIssuanceService.findByCouponId(COUPON_ID)).thenReturn(Optional.of(couponIssuance));
        when(couponIssuance.getCoupon()).thenReturn(coupon);

        assertThatThrownBy(() -> getMyCouponUsageHistoryUseCase.find(USER_ID, COUPON_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
        verifyNoInteractions(couponRedemptionService);
    }

    private AppUser user(Long userId) {
        AppUser user = mock(AppUser.class);
        when(user.getUserId()).thenReturn(userId);
        return user;
    }

    private Coupon coupon(AppUser user) {
        Coupon coupon = mock(Coupon.class);
        when(coupon.getUser()).thenReturn(user);
        return coupon;
    }
}
