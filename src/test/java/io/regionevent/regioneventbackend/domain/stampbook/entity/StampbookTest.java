package io.regionevent.regioneventbackend.domain.stampbook.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.region.entity.Region;

class StampbookTest {

    private Stampbook stampbook;

    @BeforeEach
    void setUp() {
        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(1L);

        CouponPolicy rewardCouponPolicy = mock(CouponPolicy.class);
        when(rewardCouponPolicy.getIssuanceType()).thenReturn(CouponIssuanceType.STAMPBOOK_COMPLETION);
        when(rewardCouponPolicy.getRegion()).thenReturn(region);

        stampbook = new Stampbook(region, rewardCouponPolicy);
    }

    @Test
    void reject_심사대기스탬프북을초안으로되돌린다() {
        stampbook.requestPublication();

        stampbook.reject();

        assertThat(stampbook.getStatus()).isEqualTo(StampbookStatus.DRAFT);
        assertThat(stampbook.getPublishedAt()).isNull();
        assertThat(stampbook.getEndedAt()).isNull();
    }

    @Test
    void reject_심사대기가아닌스탬프북이면예외를던진다() {
        assertThatThrownBy(stampbook::reject)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("only PENDING_REVIEW stampbook can be rejected");
    }
}
