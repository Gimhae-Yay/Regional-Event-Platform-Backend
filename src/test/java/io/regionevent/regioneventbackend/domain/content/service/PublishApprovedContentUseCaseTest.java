package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;

class PublishApprovedContentUseCaseTest {

    private static final Long CONTENT_ID = 1L;
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000380");
    private static final Instant DATABASE_TIME = Instant.parse("2026-08-05T00:00:00Z");

    @Test
    void publish_공개_대상이_아니면_건너뛴다() {
        ContentService contentService = mock(ContentService.class);
        ContentLogService contentLogService = mock(ContentLogService.class);
        RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase = mock(
            RecordFailedAuditEventUseCase.class
        );
        when(contentService.findApprovedPublicationTargetForUpdate(CONTENT_ID)).thenReturn(Optional.empty());
        PublishApprovedContentUseCase useCase = new PublishApprovedContentUseCase(
            contentService,
            contentLogService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase
        );

        PublishApprovedContentResult result = useCase.publish(CONTENT_ID, REQUEST_ID);

        assertThat(result).isEqualTo(PublishApprovedContentResult.SKIPPED);
        verify(contentService, never()).publish(any());
        verify(contentLogService, never()).recordPublished(any(), any());
        verify(recordAuditEventUseCase, never()).record(any());
    }

    @Test
    void publish_승인_콘텐츠를_공개하고_시스템_로그와_성공_감사를_기록한다() {
        ContentService contentService = mock(ContentService.class);
        ContentLogService contentLogService = mock(ContentLogService.class);
        RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase = mock(
            RecordFailedAuditEventUseCase.class
        );
        Content content = mock(Content.class);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(contentService.findApprovedPublicationTargetForUpdate(CONTENT_ID)).thenReturn(Optional.of(content));
        when(contentService.findCurrentDatabaseTime()).thenReturn(DATABASE_TIME);
        when(contentService.publish(content)).thenReturn(content);
        PublishApprovedContentUseCase useCase = new PublishApprovedContentUseCase(
            contentService,
            contentLogService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase
        );

        PublishApprovedContentResult result = useCase.publish(CONTENT_ID, REQUEST_ID);

        assertThat(result).isEqualTo(PublishApprovedContentResult.PUBLISHED);
        verify(contentLogService).recordPublished(content, DATABASE_TIME);
        verify(recordAuditEventUseCase).record(any(AuditEventCommand.class));
        verify(recordFailedAuditEventUseCase, never()).record(any());
    }

    @Test
    void publish_상태_변경_중_예외가_발생하면_실패_감사를_등록하고_예외를_전파한다() {
        ContentService contentService = mock(ContentService.class);
        ContentLogService contentLogService = mock(ContentLogService.class);
        RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase = mock(
            RecordFailedAuditEventUseCase.class
        );
        Content content = mock(Content.class);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(contentService.findApprovedPublicationTargetForUpdate(CONTENT_ID)).thenReturn(Optional.of(content));
        when(contentService.findCurrentDatabaseTime()).thenReturn(DATABASE_TIME);
        when(contentService.publish(content)).thenThrow(new IllegalStateException("publish failed"));
        PublishApprovedContentUseCase useCase = new PublishApprovedContentUseCase(
            contentService,
            contentLogService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase
        );

        assertThatThrownBy(() -> useCase.publish(CONTENT_ID, REQUEST_ID))
            .isInstanceOf(IllegalStateException.class);

        verify(recordFailedAuditEventUseCase).record(any(AuditEventCommand.class));
        verify(recordAuditEventUseCase, never()).record(any());
    }
}
