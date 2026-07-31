package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ContentHistoryTargetServiceTest {

    private static final Long CONTENT_ID = 10L;
    private static final Long REGION_ID = 20L;

    private final ContentRepository contentRepository = mock(ContentRepository.class);
    private final ContentHistoryTargetService contentHistoryTargetService =
        new ContentHistoryTargetService(contentRepository);

    @Test
    void findById_whenContentExists_returnsContentAndRegionIds() {
        Content content = mock(Content.class);
        Region region = mock(Region.class);
        when(contentRepository.findByContentId(CONTENT_ID)).thenReturn(Optional.of(content));
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getRegion()).thenReturn(region);
        when(region.getRegionId()).thenReturn(REGION_ID);

        ContentHistoryTarget target = contentHistoryTargetService.findById(CONTENT_ID);

        assertThat(target).isEqualTo(new ContentHistoryTarget(CONTENT_ID, REGION_ID));
    }

    @Test
    void findById_whenContentDoesNotExist_throwsNotFound() {
        when(contentRepository.findByContentId(CONTENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contentHistoryTargetService.findById(CONTENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );
    }
}
