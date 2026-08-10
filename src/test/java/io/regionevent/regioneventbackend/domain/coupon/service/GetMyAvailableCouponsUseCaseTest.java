package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetMyAvailableCouponsUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long HOLD_ID = 200L;
    private static final Instant EVALUATED_AT = Instant.parse("2026-08-10T00:00:00Z");

    private final AppUserService appUserService = mock(AppUserService.class);
    private final CapacityHoldService capacityHoldService = mock(CapacityHoldService.class);
    private final CouponService couponService = mock(CouponService.class);
    private final Clock clock = mock(Clock.class);
    private final GetMyAvailableCouponsUseCase useCase = new GetMyAvailableCouponsUseCase(
        appUserService,
        capacityHoldService,
        couponService,
        clock
    );

    @Test
    void findAll_적용_조건을_만족하는_AVAILABLE_쿠폰만_반환한다() {
        AppUser user = mock(AppUser.class);
        CapacityHold hold = hold(10L, 101L, 2, CapacityHoldStatus.ACTIVE, EVALUATED_AT.plusSeconds(60));
        Coupon applicableCoupon = coupon(300L, 400L, 101L, 10L, 3_000L, 10_000L, EVALUATED_AT.plusSeconds(60));
        Coupon expiredCoupon = coupon(301L, 401L, 101L, 10L, 3_000L, 10_000L, EVALUATED_AT);
        Coupon otherContentCoupon = coupon(302L, 402L, 102L, 10L, 3_000L, 10_000L, EVALUATED_AT.plusSeconds(60));
        Coupon insufficientAmountCoupon = coupon(303L, 403L, 101L, 10L, 3_000L, 30_000L, EVALUATED_AT.plusSeconds(60));
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(capacityHoldService.findOwnedHold(HOLD_ID, user)).thenReturn(hold);
        when(couponService.findAllByUserId(USER_ID, CouponStatus.AVAILABLE)).thenReturn(List.of(
            applicableCoupon,
            expiredCoupon,
            otherContentCoupon,
            insufficientAmountCoupon
        ));
        when(clock.instant()).thenReturn(EVALUATED_AT);

        GetMyAvailableCouponsResult result = useCase.findAll(USER_ID, HOLD_ID);

        assertThat(result.holdId()).isEqualTo(HOLD_ID);
        assertThat(result.evaluatedAt()).isEqualTo(EVALUATED_AT);
        assertThat(result.availableCoupons()).singleElement().satisfies(coupon -> {
            assertThat(coupon.coupon().couponId()).isEqualTo(300L);
            assertThat(coupon.baseAmount()).isEqualTo(20_000L);
            assertThat(coupon.discountAmount()).isEqualTo(3_000L);
            assertThat(coupon.payableAmount()).isEqualTo(17_000L);
        });
        verify(couponService).findAllByUserId(USER_ID, CouponStatus.AVAILABLE);
    }

    @Test
    void findAll_비활성_홀드면_충돌을_반환한다() {
        AppUser user = mock(AppUser.class);
        CapacityHold hold = hold(10L, 101L, 1, CapacityHoldStatus.CONSUMED, EVALUATED_AT.plusSeconds(60));
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(capacityHoldService.findOwnedHold(HOLD_ID, user)).thenReturn(hold);
        when(clock.instant()).thenReturn(EVALUATED_AT);

        assertThatThrownBy(() -> useCase.findAll(USER_ID, HOLD_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COUPON_AVAILABILITY_CONFLICT)
            );
    }

    private CapacityHold hold(
        Long regionId,
        Long contentId,
        int quantity,
        CapacityHoldStatus status,
        Instant expiresAt
    ) {
        CapacityHold hold = mock(CapacityHold.class);
        Region region = mock(Region.class);
        ContentSession session = mock(ContentSession.class);
        Content content = mock(Content.class);
        when(hold.getRegion()).thenReturn(region);
        when(hold.getContentSession()).thenReturn(session);
        when(hold.getQuantity()).thenReturn(quantity);
        when(hold.getStatus()).thenReturn(status);
        when(hold.getExpiresAt()).thenReturn(expiresAt);
        when(region.getRegionId()).thenReturn(regionId);
        when(session.getContent()).thenReturn(content);
        when(content.getContentId()).thenReturn(contentId);
        when(content.getReservationPrice()).thenReturn(10_000L);
        return hold;
    }

    private Coupon coupon(
        Long couponId,
        Long policyId,
        Long contentId,
        Long regionId,
        long discountAmount,
        long minimumPaymentAmount,
        Instant expiresAt
    ) {
        Coupon coupon = mock(Coupon.class);
        CouponPolicy policy = mock(CouponPolicy.class);
        Content content = mock(Content.class);
        Region region = mock(Region.class);
        when(coupon.getCouponId()).thenReturn(couponId);
        when(coupon.getCouponPolicy()).thenReturn(policy);
        when(coupon.getStatus()).thenReturn(CouponStatus.AVAILABLE);
        when(coupon.getIssuedAt()).thenReturn(EVALUATED_AT.minusSeconds(60));
        when(coupon.getExpiresAt()).thenReturn(expiresAt);
        when(policy.getCouponPolicyId()).thenReturn(policyId);
        when(policy.getContent()).thenReturn(content);
        when(policy.getRegion()).thenReturn(region);
        when(policy.getName()).thenReturn("할인 쿠폰");
        when(policy.getIssuanceType()).thenReturn(CouponIssuanceType.VISIT);
        when(policy.getDiscountAmount()).thenReturn(discountAmount);
        when(policy.getMinimumPaymentAmount()).thenReturn(minimumPaymentAmount);
        when(content.getContentId()).thenReturn(contentId);
        when(region.getRegionId()).thenReturn(regionId);
        return coupon;
    }
}
