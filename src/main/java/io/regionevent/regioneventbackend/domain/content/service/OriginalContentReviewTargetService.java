package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
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

    @Transactional(readOnly = true)
    public List<OriginalContentReviewTarget> findByContents(List<Content> contents) {
        if (contents.isEmpty()) {
            return List.of();
        }

        List<Long> contentIds = contents.stream()
            .map(Content::getContentId)
            .toList();
        Map<Long, List<ContentLog>> latestLogsByContentId = contentLogRepository
            .findLatestTwoByContentIds(contentIds)
            .stream()
            .collect(Collectors.groupingBy(
                contentLog -> contentLog.getContent().getContentId()
            ));

        return contents.stream()
            .map(content -> classify(content, latestLogsByContentId))
            .toList();
    }

    private OriginalContentReviewTarget classify(
        Content content,
        Map<Long, List<ContentLog>> latestLogsByContentId
    ) {
        List<ContentLog> latestLogs = latestLogsByContentId.get(content.getContentId());
        if (latestLogs == null || latestLogs.isEmpty()) {
            throw new IllegalStateException("pending content must have content logs");
        }
        return originalContentReviewTargetPolicy.classify(latestLogs);
    }
}
