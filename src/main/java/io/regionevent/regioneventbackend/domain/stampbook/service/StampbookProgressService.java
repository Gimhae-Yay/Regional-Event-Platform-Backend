package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgress;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookProgressRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Service
public class StampbookProgressService {

    private final StampbookProgressRepository stampbookProgressRepository;

    public StampbookProgressService(StampbookProgressRepository stampbookProgressRepository) {
        this.stampbookProgressRepository = stampbookProgressRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StampbookProgress findOrCreateForUpdate(
        Stampbook stampbook,
        AppUser user
    ) {
        return stampbookProgressRepository.findByStampbookIdAndUserIdForUpdate(
                stampbook.getStampbookId(),
                user.getUserId()
            )
            .orElseGet(() -> stampbookProgressRepository.saveAndFlush(
                new StampbookProgress(stampbook, user)
            ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(
        StampbookProgress progress,
        Instant completedAt
    ) {
        progress.complete(completedAt);
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
