package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class PublishApprovedContentsUseCaseTest {

    @Test
    void publishApprovedContents_후보가_없으면_모든_처리_결과를_0으로_반환한다() {
        ContentService contentService = mock(ContentService.class);
        PublishApprovedContentUseCase publishApprovedContentUseCase = mock(
            PublishApprovedContentUseCase.class
        );
        when(contentService.findApprovedPublicationCandidateIds()).thenReturn(List.of());
        PublishApprovedContentsUseCase useCase = new PublishApprovedContentsUseCase(
            contentService,
            publishApprovedContentUseCase
        );

        PublishApprovedContentsResult result = useCase.publishApprovedContents();

        assertThat(result.candidateContentCount()).isZero();
        assertThat(result.publishedContentCount()).isZero();
        assertThat(result.skippedContentCount()).isZero();
        assertThat(result.failedContentCount()).isZero();
        assertThat(result.publicationDelays()).isEmpty();
        verifyNoInteractions(publishApprovedContentUseCase);
    }

    @Test
    void publishApprovedContents_실패한_후보가_있어도_다음_후보를_계속_처리한다() {
        ContentService contentService = mock(ContentService.class);
        PublishApprovedContentUseCase publishApprovedContentUseCase = mock(
            PublishApprovedContentUseCase.class
        );
        when(contentService.findApprovedPublicationCandidateIds()).thenReturn(List.of(1L, 2L, 3L));
        when(publishApprovedContentUseCase.publish(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
            .thenReturn(PublishApprovedContentResult.published(Duration.ofSeconds(10)));
        when(publishApprovedContentUseCase.publish(org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.any()))
            .thenThrow(new IllegalStateException("publication failed"));
        when(publishApprovedContentUseCase.publish(org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.any()))
            .thenReturn(PublishApprovedContentResult.skipped());
        PublishApprovedContentsUseCase useCase = new PublishApprovedContentsUseCase(
            contentService,
            publishApprovedContentUseCase
        );

        PublishApprovedContentsResult result = useCase.publishApprovedContents();

        assertThat(result.candidateContentCount()).isEqualTo(3);
        assertThat(result.publishedContentCount()).isEqualTo(1);
        assertThat(result.skippedContentCount()).isEqualTo(1);
        assertThat(result.failedContentCount()).isEqualTo(1);
        assertThat(result.publicationDelays()).containsExactly(Duration.ofSeconds(10));
        verify(publishApprovedContentUseCase).publish(org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishApprovedContents_모든_후보가_실패하면_실패_수만_반환한다() {
        ContentService contentService = mock(ContentService.class);
        PublishApprovedContentUseCase publishApprovedContentUseCase = mock(
            PublishApprovedContentUseCase.class
        );
        when(contentService.findApprovedPublicationCandidateIds()).thenReturn(List.of(1L, 2L));
        when(publishApprovedContentUseCase.publish(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
            .thenThrow(new IllegalStateException("publication failed"));
        PublishApprovedContentsUseCase useCase = new PublishApprovedContentsUseCase(
            contentService,
            publishApprovedContentUseCase
        );

        PublishApprovedContentsResult result = useCase.publishApprovedContents();

        assertThat(result.candidateContentCount()).isEqualTo(2);
        assertThat(result.publishedContentCount()).isZero();
        assertThat(result.skippedContentCount()).isZero();
        assertThat(result.failedContentCount()).isEqualTo(2);
        assertThat(result.publicationDelays()).isEmpty();
        verify(publishApprovedContentUseCase).publish(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any());
        verify(publishApprovedContentUseCase).publish(org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishApprovedContents_후보_실패를_비개인_구조화_로그로_기록한다() {
        ContentService contentService = mock(ContentService.class);
        PublishApprovedContentUseCase publishApprovedContentUseCase = mock(
            PublishApprovedContentUseCase.class
        );
        when(contentService.findApprovedPublicationCandidateIds()).thenReturn(List.of(1L));
        when(publishApprovedContentUseCase.publish(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
            .thenThrow(new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT));
        PublishApprovedContentsUseCase useCase = new PublishApprovedContentsUseCase(
            contentService,
            publishApprovedContentUseCase
        );
        Logger logger = (Logger) LoggerFactory.getLogger(PublishApprovedContentsUseCase.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            PublishApprovedContentsResult result = useCase.publishApprovedContents();

            assertThat(result.failedContentCount()).isEqualTo(1);
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage())
                    .isEqualTo("Approved content publication candidate failed");
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getKeyValuePairs())
                    .extracting(pair -> pair.key, pair -> pair.value)
                    .containsExactly(
                        tuple("requestId", result.requestId()),
                        tuple("contentId", 1L),
                        tuple("failureCode", ErrorCode.CONTENT_STATE_CONFLICT.code())
                    );
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void publishApprovedContents_예상하지못한후보실패는스택트레이스와함께오류로그로기록한다() {
        ContentService contentService = mock(ContentService.class);
        PublishApprovedContentUseCase publishApprovedContentUseCase = mock(
            PublishApprovedContentUseCase.class
        );
        IllegalStateException exception = new IllegalStateException("publication failed");
        when(contentService.findApprovedPublicationCandidateIds()).thenReturn(List.of(1L));
        when(publishApprovedContentUseCase.publish(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
            .thenThrow(exception);
        PublishApprovedContentsUseCase useCase = new PublishApprovedContentsUseCase(
            contentService,
            publishApprovedContentUseCase
        );
        Logger logger = (Logger) LoggerFactory.getLogger(PublishApprovedContentsUseCase.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            PublishApprovedContentsResult result = useCase.publishApprovedContents();

            assertThat(result.failedContentCount()).isEqualTo(1);
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getKeyValuePairs())
                    .extracting(pair -> pair.key, pair -> pair.value)
                    .containsExactly(
                        tuple("requestId", result.requestId()),
                        tuple("contentId", 1L),
                        tuple("failureCode", ErrorCode.INTERNAL_SERVER_ERROR.code())
                    );
                assertThat(event.getThrowableProxy()).isNotNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
