package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class NoShowAndSessionCompletionSchedulerTest {

    @Test
    void 스케줄러가_실행되면_노쇼와_회차완료_유스케이스를_호출한다() {
        ExpireNoShowsAndCompleteSessionUseCase useCase = mock(ExpireNoShowsAndCompleteSessionUseCase.class);
        when(useCase.execute()).thenReturn(new NoShowAndSessionCompletionResult(
            UUID.randomUUID(),
            2,
            1,
            0
        ));
        NoShowAndSessionCompletionScheduler scheduler = new NoShowAndSessionCompletionScheduler(useCase);

        scheduler.expireNoShowsAndCompleteSessions();

        verify(useCase).execute();
    }
}
