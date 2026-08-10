package io.regionevent.regioneventbackend.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class CouponExpirationSchedulerTest {

    @Test
    void 스케줄러가_쿠폰_만료_유스케이스를_호출한다() {
        ExpireCouponsUseCase useCase = mock(ExpireCouponsUseCase.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(useCase.execute()).thenReturn(new CouponExpirationResult(
            UUID.randomUUID(),
            1,
            2,
            2,
            0,
            0
        ));
        CouponExpirationScheduler scheduler = new CouponExpirationScheduler(useCase, meterRegistry);

        scheduler.expireCoupons();

        verify(useCase).execute();
        assertThat(meterRegistry.get("coupon.expiration.execution").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("coupon.expiration.batch").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("coupon.expiration.candidate").counter().count()).isEqualTo(2);
        assertThat(meterRegistry.get("coupon.expiration.expired").counter().count()).isEqualTo(2);
        assertThat(meterRegistry.get("coupon.expiration.zero-update").counter().count()).isZero();
        assertThat(meterRegistry.get("coupon.expiration.failure").counter().count()).isZero();
        assertThat(meterRegistry.get("coupon.expiration.execution.time").timer().count()).isEqualTo(1);
    }
}
