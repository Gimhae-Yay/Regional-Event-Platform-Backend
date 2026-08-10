package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class CouponExpirationSchedulerTest {

    @Test
    void 스케줄러가_쿠폰_만료_유스케이스를_호출한다() {
        ExpireCouponsUseCase useCase = mock(ExpireCouponsUseCase.class);
        when(useCase.execute()).thenReturn(new CouponExpirationResult(
            UUID.randomUUID(),
            1,
            2,
            2,
            0,
            0
        ));
        CouponExpirationScheduler scheduler = new CouponExpirationScheduler(useCase);

        scheduler.expireCoupons();

        verify(useCase).execute();
    }
}
