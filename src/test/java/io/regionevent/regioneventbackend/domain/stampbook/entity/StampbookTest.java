package io.regionevent.regioneventbackend.domain.stampbook.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.region.entity.Region;

class StampbookTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-14T03:00:00Z");

    @Test
    void approve_심사대기스탬프북을공개하고공개시각을기록한다() {
        Stampbook stampbook = pendingReviewStampbook();

        stampbook.approve(PUBLISHED_AT);

        assertThat(stampbook.getStatus()).isEqualTo(StampbookStatus.PUBLISHED);
        assertThat(stampbook.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(stampbook.getEndedAt()).isNull();
    }

    @Test
    void approve_심사대기가아닌스탬프북이면거부한다() {
        Stampbook stampbook = new Stampbook(region(), rewardCouponPolicy());

        assertThatThrownBy(() -> stampbook.approve(PUBLISHED_AT))
            .isInstanceOf(IllegalStateException.class);
    }

    private Stampbook pendingReviewStampbook() {
        Stampbook stampbook = new Stampbook(region(), rewardCouponPolicy());
        stampbook.requestPublication();
        return stampbook;
    }

    private Region region() {
        return new Region("GIMHAE", "김해", true);
    }

    private CouponPolicy rewardCouponPolicy() {
        CouponPolicy couponPolicy = mock(CouponPolicy.class);
        when(couponPolicy.getIssuanceType()).thenReturn(CouponIssuanceType.STAMPBOOK_COMPLETION);
        when(couponPolicy.getRegion()).thenReturn(region());
        return couponPolicy;
    }
}
