package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;

@Service
public class OriginalContentReviewTargetService {

    private final ContentLogRepository contentLogRepository;
    private final OriginalContentReviewTargetPolicy originalContentReviewTargetPolicy;

    public OriginalContentReviewTargetService(
        ContentLogRepository contentLogRepository,
        OriginalContentReviewTargetPolicy originalContentReviewTargetPolicy
    ) {
        this.contentLogRepository = contentLogRepository;
        this.originalContentReviewTargetPolicy = originalContentReviewTargetPolicy;
    }

    @Transactional(readOnly = true)
    public Optional<OriginalContentReviewTarget> findByContentId(Long contentId) {
        List<ContentLog> latestLogs = contentLogRepository
            .findTop2ByContentContentIdAndContentDeletedAtIsNullAndContentStatusOrderByDateDescIdDesc(
                contentId,
                ContentStatus.PENDING
            );
        if (latestLogs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(originalContentReviewTargetPolicy.classify(latestLogs));
    }
}
