package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampEarn;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgress;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookRewardGrant;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;

@Service
public class RecordStampbookProgressUseCase {

    private final VisitService visitService;
    private final StampbookService stampbookService;
    private final StampbookContentService stampbookContentService;
    private final StampbookProgressService stampbookProgressService;
    private final StampEarnService stampEarnService;
    private final StampbookRewardGrantService stampbookRewardGrantService;

    public RecordStampbookProgressUseCase(
        VisitService visitService,
        StampbookService stampbookService,
        StampbookContentService stampbookContentService,
        StampbookProgressService stampbookProgressService,
        StampEarnService stampEarnService,
        StampbookRewardGrantService stampbookRewardGrantService
    ) {
        this.visitService = visitService;
        this.stampbookService = stampbookService;
        this.stampbookContentService = stampbookContentService;
        this.stampbookProgressService = stampbookProgressService;
        this.stampEarnService = stampEarnService;
        this.stampbookRewardGrantService = stampbookRewardGrantService;
    }

    @Transactional
    public void record(Long visitId) {
        Visit sourceVisit = visitService.findStampbookProgressSource(visitId).orElse(null);
        if (sourceVisit == null) {
            return;
        }
        recordInCurrentTransaction(
            visitId,
            sourceVisit.getContent().getContentId()
        );
    }

    private void recordInCurrentTransaction(
        Long visitId,
        Long targetContentId
    ) {
        List<Stampbook> stampbooks = stampbookService.findPublishedByTargetContentIdForUpdate(
            targetContentId
        );
        if (stampbooks.isEmpty()) {
            return;
        }

        Visit visit = visitService.findStampbookProgressSourceInCurrentTransaction(visitId).orElse(null);
        if (visit == null) {
            return;
        }

        Instant operationAt = stampbookService.findCurrentDatabaseTime();
        for (Stampbook stampbook : stampbooks) {
            recordForStampbook(stampbook, visit, operationAt);
        }
    }

    private void recordForStampbook(
        Stampbook stampbook,
        Visit visit,
        Instant operationAt
    ) {
        if (visit.getCheckedAt().isBefore(stampbook.getPublishedAt())) {
            return;
        }

        StampbookProgress progress = stampbookProgressService.findOrCreateForUpdate(
            stampbook,
            visit.getUser()
        );
        if (progress.getStatus() != StampbookProgressStatus.IN_PROGRESS) {
            return;
        }

        Long progressId = progress.getStampbookProgressId();
        Long visitId = visit.getVisitId();
        Long contentId = visit.getContent().getContentId();
        List<StampEarn> stampEarns = stampEarnService.findAllByProgressIdForUpdate(progressId);
        if (hasDuplicateEarn(stampEarns, visitId, contentId)) {
            return;
        }

        stampEarnService.create(new StampEarn(progress, visit, visit.getContent(), operationAt));
        if (stampEarns.size() + 1
            < stampbookContentService.countTargetContents(stampbook.getStampbookId())) {
            return;
        }

        stampbookProgressService.complete(progress, operationAt);
        stampbookRewardGrantService.create(new StampbookRewardGrant(
            progress,
            stampbook.getRewardCouponPolicy(),
            operationAt
        ));
    }

    private boolean hasDuplicateEarn(
        List<StampEarn> stampEarns,
        Long visitId,
        Long contentId
    ) {
        return stampEarns.stream().anyMatch(stampEarn ->
            visitId.equals(stampEarn.getVisit().getVisitId())
                || contentId.equals(stampEarn.getContent().getContentId())
        );
    }
}
