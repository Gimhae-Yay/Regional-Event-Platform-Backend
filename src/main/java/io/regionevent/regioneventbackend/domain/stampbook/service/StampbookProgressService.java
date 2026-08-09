package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgress;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookProgressRepository;

@Service
public class StampbookProgressService {

    private final StampbookProgressRepository stampbookProgressRepository;

    public StampbookProgressService(StampbookProgressRepository stampbookProgressRepository) {
        this.stampbookProgressRepository = stampbookProgressRepository;
    }

    public void endIncompleteProgresses(Long stampbookId) {
        if (stampbookId == null || stampbookId <= 0) {
            throw new IllegalArgumentException("stampbookId must be positive");
        }
        List<StampbookProgress> progresses = stampbookProgressRepository
            .findByStampbookIdAndStatusForUpdate(stampbookId, StampbookProgressStatus.IN_PROGRESS);
        progresses.forEach(StampbookProgress::endIncomplete);
    }
}
