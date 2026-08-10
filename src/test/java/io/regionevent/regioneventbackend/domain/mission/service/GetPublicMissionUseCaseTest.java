package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;

class GetPublicMissionUseCaseTest {

    @Test
    void get_anonymous_doesNotQueryParticipation() {
        MissionService missionService = mock(MissionService.class);
        MissionParticipationReadService missionParticipationReadService = mock(MissionParticipationReadService.class);
        Clock clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
        Mission mission = mock(Mission.class);
        when(missionService.findPublicMissionDetail(701L, Instant.now(clock))).thenReturn(mission);

        PublicMissionDetailResult result = new GetPublicMissionUseCase(
            missionService,
            missionParticipationReadService,
            clock
        ).get(701L, null);

        assertThat(result.mission()).isSameAs(mission);
        assertThat(result.participation()).isNull();
        verify(missionService).findPublicMissionDetail(701L, Instant.now(clock));
        verifyNoInteractions(missionParticipationReadService);
    }

    @Test
    void get_authenticated_combinesOwnParticipation() {
        MissionService missionService = mock(MissionService.class);
        MissionParticipationReadService missionParticipationReadService = mock(MissionParticipationReadService.class);
        Clock clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
        Mission mission = mock(Mission.class);
        MissionParticipationSummary participation = new MissionParticipationSummary(
            9001L,
            701L,
            MissionParticipationStatus.IN_PROGRESS,
            1,
            3,
            false,
            Instant.parse("2029-12-01T00:00:00Z"),
            null
        );
        when(missionService.findPublicMissionDetail(701L, Instant.now(clock))).thenReturn(mission);
        when(missionParticipationReadService.findSummaryByMissionIdAndUserId(701L, 100L))
            .thenReturn(Optional.of(participation));

        PublicMissionDetailResult result = new GetPublicMissionUseCase(
            missionService,
            missionParticipationReadService,
            clock
        ).get(701L, 100L);

        assertThat(result.participation()).isEqualTo(participation);
        verify(missionParticipationReadService).findSummaryByMissionIdAndUserId(701L, 100L);
        verify(missionParticipationReadService, never()).findSummaryByMissionIdAndUserId(701L, null);
    }
}
