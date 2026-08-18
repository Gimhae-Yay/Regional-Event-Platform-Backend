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

        stampbook = new Stampbook(region, rewardCouponPolicy, "  김해 문화 완주  ");
    }

    @Test
    void 생성_제목의앞뒤공백을제거한다() {
        assertThat(stampbook.getTitle()).isEqualTo("김해 문화 완주");
    }

    @Test
    void updateTitle_초안스탬프북의제목을변경하고앞뒤공백을제거한다() {
        stampbook.updateTitle("  가야 문화 완주  ");

        assertThat(stampbook.getTitle()).isEqualTo("가야 문화 완주");
    }

    @Test
    void updateTitle_공백제목은거부한다() {
        assertThatThrownBy(() -> stampbook.updateTitle("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateTitle_초안이아닌스탬프북이면거부한다() {
        stampbook.requestPublication();

        assertThatThrownBy(() -> stampbook.updateTitle("가야 문화 완주"))
            .isInstanceOf(IllegalStateException.class);

        stampbook.approve(PUBLISHED_AT);

        assertThatThrownBy(() -> stampbook.updateTitle("가야 문화 완주"))
            .isInstanceOf(IllegalStateException.class);

        stampbook.end(PUBLISHED_AT.plusSeconds(60));

        assertThatThrownBy(() -> stampbook.updateTitle("가야 문화 완주"))
            .isInstanceOf(IllegalStateException.class);
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
