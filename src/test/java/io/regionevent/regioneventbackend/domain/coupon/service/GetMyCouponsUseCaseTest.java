package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;

@ExtendWith(MockitoExtension.class)
class GetMyCouponsUseCaseTest {

    private static final Long USER_ID = 100L;

    @Mock
    private AppUserService appUserService;

    @Mock
    private CouponService couponService;

    @InjectMocks
    private GetMyCouponsUseCase getMyCouponsUseCase;

    @Test
    void findAll_상태필터가_있으면_활성_회원의_해당_상태_쿠폰만_조회한다() {
        when(couponService.findAllByUserId(USER_ID, CouponStatus.AVAILABLE)).thenReturn(List.of());

        List<CouponSummary> result = getMyCouponsUseCase.findAll(USER_ID, CouponStatus.AVAILABLE);

        assertThat(result).isEmpty();
        verify(appUserService).findActiveUser(USER_ID);
        verify(couponService).findAllByUserId(USER_ID, CouponStatus.AVAILABLE);
    }

    @Test
    void findAll_상태필터가_없으면_활성_회원의_전체_쿠폰을_조회한다() {
        when(couponService.findAllByUserId(USER_ID, null)).thenReturn(List.of());

        List<CouponSummary> result = getMyCouponsUseCase.findAll(USER_ID, null);

        assertThat(result).isEmpty();
        verify(appUserService).findActiveUser(USER_ID);
        verify(couponService).findAllByUserId(USER_ID, null);
    }
}
