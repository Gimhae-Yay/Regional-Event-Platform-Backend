package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetPublicContentSessionsUseCaseTest {

    private static final Long CONTENT_ID = 10L;

    private final ContentService contentService = mock(ContentService.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final GetPublicContentSessionsUseCase getPublicContentSessionsUseCase =
        new GetPublicContentSessionsUseCase(contentService, contentSessionService);

    @Test
    void 공개_콘텐츠의_회차를_콘텐츠_확인_후_반환한다() {
        List<ContentSession> expected = List.of(mock(ContentSession.class), mock(ContentSession.class));
        when(contentService.existsPublicPublishedAndNotDeletedById(CONTENT_ID)).thenReturn(true);
        when(contentSessionService.findScheduledByContentId(CONTENT_ID)).thenReturn(expected);

        List<ContentSession> result = getPublicContentSessionsUseCase.get(CONTENT_ID);

        assertThat(result).isSameAs(expected);
        InOrder inOrder = inOrder(contentService, contentSessionService);
        inOrder.verify(contentService).existsPublicPublishedAndNotDeletedById(CONTENT_ID);
        inOrder.verify(contentSessionService).findScheduledByContentId(CONTENT_ID);
    }

    @Test
    void 공개_콘텐츠에_SCHEDULED_회차가_없으면_빈_목록을_반환한다() {
        when(contentService.existsPublicPublishedAndNotDeletedById(CONTENT_ID)).thenReturn(true);
        when(contentSessionService.findScheduledByContentId(CONTENT_ID)).thenReturn(List.of());

        List<ContentSession> result = getPublicContentSessionsUseCase.get(CONTENT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void 공개_지역의_게시되고_삭제되지_않은_콘텐츠가_없으면_찾을수없음을_반환한다() {
        when(contentService.existsPublicPublishedAndNotDeletedById(CONTENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> getPublicContentSessionsUseCase.get(CONTENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );
        verifyNoInteractions(contentSessionService);
    }
}
