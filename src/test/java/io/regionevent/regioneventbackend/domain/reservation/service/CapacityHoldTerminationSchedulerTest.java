package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class CapacityHoldTerminationSchedulerTest {

    @Test
    void 스케줄러가_실행되면_홀드종결_유스케이스를_호출한다() {
        ExpireOrInvalidateCapacityHoldsUseCase useCase = mock(ExpireOrInvalidateCapacityHoldsUseCase.class);
        when(useCase.execute()).thenReturn(new HoldTerminationResult(
            UUID.randomUUID(),
            2,
            1,
            0
        ));
        CapacityHoldTerminationScheduler scheduler = new CapacityHoldTerminationScheduler(useCase);

        scheduler.expireOrInvalidateCapacityHolds();

        verify(useCase).execute();
    }
}
