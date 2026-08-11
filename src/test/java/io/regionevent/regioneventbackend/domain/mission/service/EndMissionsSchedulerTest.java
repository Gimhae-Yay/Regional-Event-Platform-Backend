package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EndMissionsSchedulerTest {

    @Test
    void endMissions_후보가없으면종료를시도하지않는다() {
        EndMissionsUseCase useCase = mock(EndMissionsUseCase.class);
        when(useCase.findAutoEndCandidateIds()).thenReturn(List.of());
        EndMissionsScheduler scheduler = new EndMissionsScheduler(useCase);

        scheduler.endMissions();

        verify(useCase).findAutoEndCandidateIds();
        verify(useCase, never()).endBySystem(any(), any());
    }

    @Test
    void endMissions_후보별종료를호출하고실패후에도다음후보를처리한다() {
        EndMissionsUseCase useCase = mock(EndMissionsUseCase.class);
        when(useCase.findAutoEndCandidateIds()).thenReturn(List.of(1L, 2L, 3L));
        when(useCase.endBySystem(eq(1L), any(UUID.class))).thenReturn(EndMissionSystemResult.ended());
        when(useCase.endBySystem(eq(2L), any(UUID.class))).thenThrow(new IllegalStateException("종료 실패"));
        when(useCase.endBySystem(eq(3L), any(UUID.class))).thenReturn(EndMissionSystemResult.skipped());
        EndMissionsScheduler scheduler = new EndMissionsScheduler(useCase);

        scheduler.endMissions();

        ArgumentCaptor<UUID> requestIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(useCase).endBySystem(eq(1L), requestIdCaptor.capture());
        verify(useCase).endBySystem(eq(2L), requestIdCaptor.capture());
        verify(useCase).endBySystem(eq(3L), requestIdCaptor.capture());
        assertThat(requestIdCaptor.getAllValues())
            .hasSize(3)
            .allSatisfy(requestId -> assertThat(requestId).isEqualTo(requestIdCaptor.getValue()));
    }

    @Test
    void endMissions_실패후다음실행에서같은후보를다시시도한다() {
        EndMissionsUseCase useCase = mock(EndMissionsUseCase.class);
        when(useCase.findAutoEndCandidateIds()).thenReturn(List.of(1L));
        when(useCase.endBySystem(eq(1L), any(UUID.class)))
            .thenThrow(new IllegalStateException("일시적 종료 실패"))
            .thenReturn(EndMissionSystemResult.ended());
        EndMissionsScheduler scheduler = new EndMissionsScheduler(useCase);

        scheduler.endMissions();
        scheduler.endMissions();

        ArgumentCaptor<UUID> requestIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(useCase, times(2)).endBySystem(eq(1L), requestIdCaptor.capture());
        assertThat(requestIdCaptor.getAllValues()).doesNotHaveDuplicates();
    }
}
