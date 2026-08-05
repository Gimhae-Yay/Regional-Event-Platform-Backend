package io.regionevent.regioneventbackend.domain.content.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ApprovedContentPublicationSchedulerTest {

    @Test
    void publishApprovedContents_스케줄러가_실행되면_자동_공개_유스케이스를_호출한다() {
        PublishApprovedContentsUseCase useCase = mock(PublishApprovedContentsUseCase.class);
        when(useCase.publishApprovedContents()).thenReturn(new PublishApprovedContentsResult(
            UUID.randomUUID(),
            0,
            0,
            0
        ));
        ApprovedContentPublicationScheduler scheduler = new ApprovedContentPublicationScheduler(useCase);

        scheduler.publishApprovedContents();

        verify(useCase).publishApprovedContents();
    }
}
