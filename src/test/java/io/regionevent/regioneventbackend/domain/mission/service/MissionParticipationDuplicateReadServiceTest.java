package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class MissionParticipationDuplicateReadServiceTest {

    private static final Long MISSION_ID = 701L;
    private static final Long USER_ID = 100L;

    private final MissionParticipationRepository missionParticipationRepository = mock(
        MissionParticipationRepository.class
    );
    private final MissionParticipationDuplicateReadService service = new MissionParticipationDuplicateReadService(
        missionParticipationRepository
    );

    @Test
    void find_공개지역의승자참여면반환한다() {
        MissionParticipation participation = participation(true);
        when(missionParticipationRepository.findByMissionMissionIdAndUserUserId(MISSION_ID, USER_ID))
            .thenReturn(Optional.of(participation));

        Optional<MissionParticipation> result = service.find(MISSION_ID, USER_ID);

        assertThat(result).containsSame(participation);
    }

    @Test
    void find_비공개지역의승자참여면찾을수없음으로숨긴다() {
        MissionParticipation participation = participation(false);
        when(missionParticipationRepository.findByMissionMissionIdAndUserUserId(MISSION_ID, USER_ID))
            .thenReturn(Optional.of(participation));

        assertThatThrownBy(() -> service.find(MISSION_ID, USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );
    }

    private MissionParticipation participation(boolean isPublic) {
        Region region = mock(Region.class);
        Mission mission = mock(Mission.class);
        MissionParticipation participation = mock(MissionParticipation.class);
        when(region.isPublic()).thenReturn(isPublic);
        when(mission.getRegion()).thenReturn(region);
        when(participation.getMission()).thenReturn(mission);
        return participation;
    }
}
