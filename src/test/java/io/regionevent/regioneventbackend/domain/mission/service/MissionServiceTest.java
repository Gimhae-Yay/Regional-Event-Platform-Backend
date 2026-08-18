package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class MissionServiceTest {

    private static final BigDecimal DATABASE_EPOCH_SECONDS = new BigDecimal("1754356800.123456");

    private final MissionRepository missionRepository = mock(MissionRepository.class);
    private final MissionService missionService = new MissionService(missionRepository);

    @Test
    void findCurrentDatabaseTime_MySQL_현재_시각을_Instant로_변환한다() {
        when(missionRepository.findCurrentEpochSeconds()).thenReturn(DATABASE_EPOCH_SECONDS);

        Instant result = missionService.findCurrentDatabaseTime();

        assertThat(result).isEqualTo(Instant.ofEpochSecond(1_754_356_800L, 123_456_000));
    }

    @Test
    void create_withInvalidTitle_convertsDomainErrorToInvalidInput() {
        Region region = mock(Region.class);
        CouponPolicy rewardCouponPolicy = rewardCouponPolicy(region);

        assertThatThrownBy(() -> missionService.create(
            " ",
            region,
            MissionConditionType.VISIT_COUNT,
            1,
            rewardCouponPolicy,
            Instant.parse("2026-09-01T00:00:00Z")
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void save_withValidTitle_flushesOnce() {
        Region region = mock(Region.class);
        Mission mission = new Mission(
            "김해 미션",
            region,
            MissionConditionType.VISIT_COUNT,
            1,
            rewardCouponPolicy(region),
            Instant.parse("2026-09-01T00:00:00Z")
        );
        when(missionRepository.saveAndFlush(mission)).thenReturn(mission);

        Mission savedMission = missionService.save(mission);

        assertThat(savedMission.getTitle()).isEqualTo("김해 미션");
        verify(missionRepository).saveAndFlush(mission);
    }

    private CouponPolicy rewardCouponPolicy(Region region) {
        CouponPolicy rewardCouponPolicy = mock(CouponPolicy.class);
        when(rewardCouponPolicy.getRegion()).thenReturn(region);
        when(rewardCouponPolicy.getIssuanceType()).thenReturn(CouponIssuanceType.MISSION_REWARD);
        when(rewardCouponPolicy.getStatus()).thenReturn(CouponPolicyStatus.DRAFT);
        return rewardCouponPolicy;
    }
}
