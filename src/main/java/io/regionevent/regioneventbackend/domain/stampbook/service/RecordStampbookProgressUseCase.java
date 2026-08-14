package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;

    public RecordStampbookProgressUseCase(
        VisitService visitService,
        StampbookService stampbookService,
        StampbookContentService stampbookContentService,
        StampbookProgressService stampbookProgressService,
        StampEarnService stampEarnService,
        StampbookRewardGrantService stampbookRewardGrantService,
        PlatformTransactionManager transactionManager
    ) {
        this.visitService = visitService;
        this.stampbookService = stampbookService;
        this.stampbookContentService = stampbookContentService;
        this.stampbookProgressService = stampbookProgressService;
        this.stampEarnService = stampEarnService;
        this.stampbookRewardGrantService = stampbookRewardGrantService;
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(Long visitId) {
        if (visitService.findStampbookProgressSource(visitId).isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> recordInCurrentTransaction(visitId));
    }

    private void recordInCurrentTransaction(Long visitId) {
        Visit visit = visitService.findStampbookProgressSourceInCurrentTransaction(visitId).orElse(null);
        if (visit == null) {
            return;
        }

        List<Stampbook> stampbooks = stampbookService.findPublishedByTargetContentIdForUpdate(
            visit.getContent().getContentId()
        );
        if (stampbooks.isEmpty()) {
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
        if (stampEarnService.existsByVisitId(progressId, visitId)
            || stampEarnService.existsByContentId(progressId, contentId)) {
            return;
        }

        stampEarnService.create(new StampEarn(progress, visit, visit.getContent(), operationAt));
        if (stampEarnService.countByProgressId(progressId)
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
}
