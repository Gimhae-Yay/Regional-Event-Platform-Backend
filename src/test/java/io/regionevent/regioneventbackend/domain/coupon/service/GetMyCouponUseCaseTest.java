package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuance;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetMyCouponUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long COUPON_ID = 200L;

    private final AppUserService appUserService = mock(AppUserService.class);
    private final CouponIssuanceService couponIssuanceService = mock(CouponIssuanceService.class);
    private final GetMyCouponUseCase getMyCouponUseCase = new GetMyCouponUseCase(
        appUserService,
        couponIssuanceService
    );

    @Test
    void 내_쿠폰_상세_조회는_없는_쿠폰을_NOT_FOUND로_처리한다() {
        AppUser user = user(USER_ID);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(couponIssuanceService.findByCouponId(COUPON_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getMyCouponUseCase.find(USER_ID, COUPON_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );
    }

    @Test
    void 내_쿠폰_상세_조회는_타인_쿠폰을_FORBIDDEN으로_처리한다() {
        AppUser user = user(USER_ID);
        AppUser otherUser = user(101L);
        Coupon coupon = mock(Coupon.class);
        CouponIssuance couponIssuance = mock(CouponIssuance.class);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(couponIssuanceService.findByCouponId(COUPON_ID)).thenReturn(Optional.of(couponIssuance));
        when(couponIssuance.getCoupon()).thenReturn(coupon);
        when(coupon.getUser()).thenReturn(otherUser);

        assertThatThrownBy(() -> getMyCouponUseCase.find(USER_ID, COUPON_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
    }

    private AppUser user(Long userId) {
        AppUser user = mock(AppUser.class);
        when(user.getUserId()).thenReturn(userId);
        return user;
    }
}
