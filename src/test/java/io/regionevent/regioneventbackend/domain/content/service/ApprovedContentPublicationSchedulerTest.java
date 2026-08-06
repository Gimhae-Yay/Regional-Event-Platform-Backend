package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ApprovedContentPublicationSchedulerTest {

    @Test
    void publishApprovedContents_스케줄러가_실행되면_자동_공개_유스케이스를_호출한다() {
        PublishApprovedContentsUseCase useCase = mock(PublishApprovedContentsUseCase.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(useCase.publishApprovedContents()).thenReturn(new PublishApprovedContentsResult(
            UUID.randomUUID(),
            3,
            1,
            1,
            1,
            List.of(Duration.ofSeconds(30))
        ));
        ApprovedContentPublicationScheduler scheduler = new ApprovedContentPublicationScheduler(
            useCase,
            meterRegistry
        );

        scheduler.publishApprovedContents();

        verify(useCase).publishApprovedContents();
        assertThat(meterRegistry.counter("content.publication.candidate").count()).isEqualTo(3);
        assertThat(meterRegistry.counter("content.publication.published").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("content.publication.skipped").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("content.publication.failed").count()).isEqualTo(1);
        assertThat(meterRegistry.timer("content.publication.delay").count()).isEqualTo(1);
        assertThat(meterRegistry.timer("content.publication.delay").totalTime(java.util.concurrent.TimeUnit.SECONDS))
            .isEqualTo(30);
        assertThat(meterRegistry.getMeters())
            .allSatisfy(meter -> assertThat(meter.getId().getTags()).isEmpty());
    }

    @Test
    void publishApprovedContents_실행_완료를_처리_결과와_공개_지연으로_구조화_로그에_기록한다() {
        PublishApprovedContentsUseCase useCase = mock(PublishApprovedContentsUseCase.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        UUID requestId = UUID.randomUUID();
        when(useCase.publishApprovedContents()).thenReturn(new PublishApprovedContentsResult(
            requestId,
            2,
            1,
            1,
            0,
            List.of(Duration.ofSeconds(30))
        ));
        ApprovedContentPublicationScheduler scheduler = new ApprovedContentPublicationScheduler(
            useCase,
            meterRegistry
        );
        Logger logger = (Logger) LoggerFactory.getLogger(ApprovedContentPublicationScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            scheduler.publishApprovedContents();

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage())
                    .isEqualTo("Approved content publication scheduler finished");
                assertThat(event.getKeyValuePairs())
                    .extracting(pair -> pair.key, pair -> pair.value)
                    .containsExactly(
                        tuple("requestId", requestId),
                        tuple("candidateContentCount", 2),
                        tuple("publishedContentCount", 1),
                        tuple("skippedContentCount", 1),
                        tuple("failedContentCount", 0),
                        tuple("maximumPublicationDelayMillis", 30_000L)
                    );
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
