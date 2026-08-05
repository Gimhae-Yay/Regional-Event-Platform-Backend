package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

class PublishApprovedContentsUseCaseTest {

    @Test
    void publishApprovedContents_실패한_후보가_있어도_다음_후보를_계속_처리한다() {
        ContentService contentService = mock(ContentService.class);
        PublishApprovedContentUseCase publishApprovedContentUseCase = mock(
            PublishApprovedContentUseCase.class
        );
        when(contentService.findApprovedPublicationCandidateIds()).thenReturn(List.of(1L, 2L, 3L));
        when(publishApprovedContentUseCase.publish(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
            .thenReturn(PublishApprovedContentResult.PUBLISHED);
        when(publishApprovedContentUseCase.publish(org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.any()))
            .thenThrow(new IllegalStateException("publication failed"));
        when(publishApprovedContentUseCase.publish(org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.any()))
            .thenReturn(PublishApprovedContentResult.SKIPPED);
        PublishApprovedContentsUseCase useCase = new PublishApprovedContentsUseCase(
            contentService,
            publishApprovedContentUseCase
        );

        PublishApprovedContentsResult result = useCase.publishApprovedContents();

        assertThat(result.publishedContentCount()).isEqualTo(1);
        assertThat(result.skippedContentCount()).isEqualTo(1);
        assertThat(result.failedContentCount()).isEqualTo(1);
        verify(publishApprovedContentUseCase).publish(org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.any());
    }
}
