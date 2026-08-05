package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class PublishApprovedContentUseCase {

    private final ContentService contentService;
    private final ContentLogService contentLogService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;

    public PublishApprovedContentUseCase(
        ContentService contentService,
        ContentLogService contentLogService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase
    ) {
        this.contentService = contentService;
        this.contentLogService = contentLogService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
    }

    @Transactional
    public PublishApprovedContentResult publish(Long contentId, UUID requestId) {
        Content content = contentService.findApprovedPublicationTargetForUpdate(contentId).orElse(null);
        if (content == null) {
            return PublishApprovedContentResult.SKIPPED;
        }

        try {
            Instant publishedAt = contentService.findCurrentDatabaseTime();
            Content publishedContent = contentService.publish(content);
            contentLogService.recordPublished(publishedContent, publishedAt);
            recordAuditEventUseCase.record(new AuditEventCommand(
                requestId,
                publishedContent.getRegion(),
                AuditEventTargetType.CONTENT,
                publishedContent.getContentId(),
                ContentStatus.APPROVED.name(),
                ContentStatus.PUBLISHED.name(),
                AuditEventResult.SUCCESS,
                null,
                null,
                publishedAt
            ));
            return PublishApprovedContentResult.PUBLISHED;
        } catch (RuntimeException exception) {
            recordFailedAuditEventUseCase.record(new AuditEventCommand(
                requestId,
                content.getRegion(),
                AuditEventTargetType.CONTENT,
                content.getContentId(),
                ContentStatus.APPROVED.name(),
                null,
                AuditEventResult.FAILURE,
                ErrorCode.INTERNAL_SERVER_ERROR.code(),
                null,
                contentService.findCurrentDatabaseTime()
            ));
            throw exception;
        }
    }
}
