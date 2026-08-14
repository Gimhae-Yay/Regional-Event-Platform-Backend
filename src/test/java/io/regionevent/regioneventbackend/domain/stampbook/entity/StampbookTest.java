package io.regionevent.regioneventbackend.domain.stampbook.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.region.entity.Region;

class StampbookTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-14T03:00:00Z");

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
    void approve_심사대기스탬프북을공개하고공개시각을기록한다() {
        stampbook.requestPublication();

        stampbook.approve(PUBLISHED_AT);

        assertThat(stampbook.getStatus()).isEqualTo(StampbookStatus.PUBLISHED);
        assertThat(stampbook.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(stampbook.getEndedAt()).isNull();
    }

    @Test
    void approve_심사대기가아닌스탬프북이면거부한다() {
        assertThatThrownBy(() -> stampbook.approve(PUBLISHED_AT))
            .isInstanceOf(IllegalStateException.class);
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
