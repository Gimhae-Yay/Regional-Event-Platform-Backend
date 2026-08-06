package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EndCompletedContentsSchedulerTest {

    @Test
    void endCompletedContents_후보가_없으면_종료를_시도하지_않는다() {
        EndContentReservationsUseCase useCase = mock(EndContentReservationsUseCase.class);
        when(useCase.findAutoEndCandidateIds()).thenReturn(List.of());
        EndCompletedContentsScheduler scheduler = new EndCompletedContentsScheduler(useCase);

        scheduler.endCompletedContents();

        verify(useCase).findAutoEndCandidateIds();
        verify(useCase, never()).endBySystem(any(), any());
    }

    @Test
    void endCompletedContents_후보별_시스템_종료를_호출하고_실패_후에도_다음_후보를_처리한다() {
        EndContentReservationsUseCase useCase = mock(EndContentReservationsUseCase.class);
        when(useCase.findAutoEndCandidateIds()).thenReturn(List.of(1L, 2L, 3L));
        when(useCase.endBySystem(eq(1L), any(UUID.class)))
            .thenReturn(EndContentReservationsSystemResult.ended(1_000L));
        when(useCase.endBySystem(eq(2L), any(UUID.class)))
            .thenThrow(new IllegalStateException("종료 실패"));
        when(useCase.endBySystem(eq(3L), any(UUID.class)))
            .thenReturn(EndContentReservationsSystemResult.skipped());
        EndCompletedContentsScheduler scheduler = new EndCompletedContentsScheduler(useCase);

        scheduler.endCompletedContents();

        ArgumentCaptor<UUID> requestIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(useCase).endBySystem(eq(1L), requestIdCaptor.capture());
        verify(useCase).endBySystem(eq(2L), requestIdCaptor.capture());
        verify(useCase).endBySystem(eq(3L), requestIdCaptor.capture());
        assertThat(requestIdCaptor.getAllValues())
            .hasSize(3)
            .allSatisfy(requestId -> assertThat(requestId).isEqualTo(requestIdCaptor.getValue()));
    }

    @Test
    void endCompletedContents_실패한_후보는_다음_실행에서_재시도한다() {
        EndContentReservationsUseCase useCase = mock(EndContentReservationsUseCase.class);
        when(useCase.findAutoEndCandidateIds()).thenReturn(List.of(1L));
        when(useCase.endBySystem(eq(1L), any(UUID.class)))
            .thenThrow(new IllegalStateException("일시적 종료 실패"))
            .thenReturn(EndContentReservationsSystemResult.ended(1_000L));
        EndCompletedContentsScheduler scheduler = new EndCompletedContentsScheduler(useCase);

        scheduler.endCompletedContents();
        scheduler.endCompletedContents();

        ArgumentCaptor<UUID> requestIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(useCase, org.mockito.Mockito.times(2)).endBySystem(eq(1L), requestIdCaptor.capture());
        assertThat(requestIdCaptor.getAllValues())
            .hasSize(2)
            .doesNotHaveDuplicates();
    }
}
