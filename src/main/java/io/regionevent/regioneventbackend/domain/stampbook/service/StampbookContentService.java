package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContent;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookContentRepository;

@Service
public class StampbookContentService {

    private final StampbookContentRepository stampbookContentRepository;

    public StampbookContentService(StampbookContentRepository stampbookContentRepository) {
        this.stampbookContentRepository = stampbookContentRepository;
    }

    public void connect(
        Stampbook stampbook,
        List<Content> contents
    ) {
        List<StampbookContent> stampbookContents = contents.stream()
            .map(content -> new StampbookContent(stampbook, content))
            .toList();
        stampbookContentRepository.saveAllAndFlush(stampbookContents);
    }

    public List<Long> findContentIds(Long stampbookId) {
        if (stampbookId == null || stampbookId <= 0) {
            throw new IllegalArgumentException("stampbookId must be positive");
        }
        return List.copyOf(stampbookContentRepository.findContentIdsByStampbookId(stampbookId));
    }

    public List<StampbookContent> findDetails(Long stampbookId) {
        if (stampbookId == null || stampbookId <= 0) {
            throw new IllegalArgumentException("stampbookId must be positive");
        }
        return List.copyOf(stampbookContentRepository.findDetailByStampbookId(stampbookId));
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public long countTargetContents(Long stampbookId) {
        return stampbookContentRepository.countByStampbookStampbookId(stampbookId);
    }

    public void replace(
        Stampbook stampbook,
        List<Content> contents
    ) {
        if (stampbook == null || stampbook.getStampbookId() == null) {
            throw new IllegalArgumentException("stampbook must be persisted");
        }
        if (contents == null || contents.isEmpty()) {
            throw new IllegalArgumentException("contents must not be empty");
        }
        stampbookContentRepository.deleteByStampbookId(stampbook.getStampbookId());
        connect(stampbook, contents);
    }
}
