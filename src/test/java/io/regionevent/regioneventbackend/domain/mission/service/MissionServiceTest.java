package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.mission.repository.MissionRepository;

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
}
