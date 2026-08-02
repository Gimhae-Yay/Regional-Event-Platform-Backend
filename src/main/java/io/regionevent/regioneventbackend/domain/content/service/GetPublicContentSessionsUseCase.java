package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetPublicContentSessionsUseCase {

    private final ContentService contentService;
    private final ContentSessionService contentSessionService;

    public GetPublicContentSessionsUseCase(
        ContentService contentService,
        ContentSessionService contentSessionService
    ) {
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
    }

    @Transactional(readOnly = true)
    public List<ContentSession> get(Long contentId) {
        if (!contentService.existsPublishedAndNotDeletedById(contentId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return contentSessionService.findScheduledByContentId(contentId);
    }
}
