package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.util.List;

import org.springframework.stereotype.Service;

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
}
