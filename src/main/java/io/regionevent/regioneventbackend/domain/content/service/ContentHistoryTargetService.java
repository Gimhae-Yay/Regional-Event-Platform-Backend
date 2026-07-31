package io.regionevent.regioneventbackend.domain.content.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ContentHistoryTargetService {

    private final ContentRepository contentRepository;

    public ContentHistoryTargetService(ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    @Transactional(readOnly = true)
    public ContentHistoryTarget findById(Long contentId) {
        Content content = contentRepository.findByContentId(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return new ContentHistoryTarget(
            content.getContentId(),
            content.getRegion().getRegionId()
        );
    }
}
