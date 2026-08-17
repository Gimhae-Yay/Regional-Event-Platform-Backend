package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.mission.repository.MissionProgressRepository;

class MissionProgressDuplicateReadServiceTest {

    private static final Long PARTICIPATION_ID = 11L;
    private static final Long VISIT_ID = 22L;

    private final MissionProgressRepository missionProgressRepository = mock(MissionProgressRepository.class);
    private final MissionProgressDuplicateReadService service = new MissionProgressDuplicateReadService(
        missionProgressRepository
    );

    @Test
    void exists_승자진행근거가있으면참을반환한다() {
        when(missionProgressRepository
            .existsByMissionParticipationMissionParticipationIdAndVisitVisitId(
                PARTICIPATION_ID,
                VISIT_ID
            )).thenReturn(true);

        boolean result = service.exists(PARTICIPATION_ID, VISIT_ID);

        assertThat(result).isTrue();
    }
}
