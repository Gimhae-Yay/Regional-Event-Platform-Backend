package io.regionevent.regioneventbackend.domain.stampbook.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampEarn;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgress;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;

class RecordStampbookProgressUseCaseTest {

    private static final Long VISIT_ID = 10L;
    private static final Long CONTENT_ID = 20L;
    private static final Long STAMPBOOK_ID = 30L;
    private static final Long PROGRESS_ID = 40L;
    private static final Instant OPERATION_AT = Instant.parse("2026-08-14T08:00:00Z");

    private final VisitService visitService = mock(VisitService.class);
    private final StampbookService stampbookService = mock(StampbookService.class);
    private final StampbookContentService stampbookContentService = mock(StampbookContentService.class);
    private final StampbookProgressService stampbookProgressService = mock(StampbookProgressService.class);
    private final StampEarnService stampEarnService = mock(StampEarnService.class);
    private final StampbookRewardGrantService stampbookRewardGrantService = mock(
        StampbookRewardGrantService.class
    );
    private final RecordStampbookProgressUseCase useCase = new RecordStampbookProgressUseCase(
        visitService,
        stampbookService,
        stampbookContentService,
        stampbookProgressService,
        stampEarnService,
        stampbookRewardGrantService
    );

    private final Visit visit = mock(Visit.class);
    private final AppUser user = mock(AppUser.class);
    private final Content content = mock(Content.class);
    private final Stampbook stampbook = mock(Stampbook.class);
    private final StampbookProgress progress = mock(StampbookProgress.class);
    private final CouponPolicy couponPolicy = mock(CouponPolicy.class);

    @BeforeEach
    void setUp() {
        when(visitService.findStampbookProgressSource(VISIT_ID)).thenReturn(Optional.of(visit));
        when(visitService.findStampbookProgressSourceInCurrentTransaction(VISIT_ID))
            .thenReturn(Optional.of(visit));
        when(visit.getVisitId()).thenReturn(VISIT_ID);
        when(visit.getUser()).thenReturn(user);
        when(visit.getContent()).thenReturn(content);
        when(visit.getCheckedAt()).thenReturn(OPERATION_AT);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(stampbookService.findPublishedByTargetContentIdForUpdate(CONTENT_ID))
            .thenReturn(List.of(stampbook));
        when(stampbookService.findCurrentDatabaseTime()).thenReturn(OPERATION_AT);
        when(stampbook.getStampbookId()).thenReturn(STAMPBOOK_ID);
        when(stampbook.getPublishedAt()).thenReturn(OPERATION_AT);
        when(stampbook.getRewardCouponPolicy()).thenReturn(couponPolicy);
        when(stampbookProgressService.findOrCreateForUpdate(stampbook, user)).thenReturn(progress);
        when(progress.getStampbookProgressId()).thenReturn(PROGRESS_ID);
        when(progress.getUser()).thenReturn(user);
        when(progress.getStampbook()).thenReturn(stampbook);
    }

    @Test
    void record_잠금조회한적립이력으로완료를판정한다() {
        StampEarn existingEarn = existingEarn(VISIT_ID + 1, CONTENT_ID + 1);
        when(progress.getStatus()).thenReturn(
            StampbookProgressStatus.IN_PROGRESS,
            StampbookProgressStatus.COMPLETED
        );
        when(stampEarnService.findAllByProgressIdForUpdate(PROGRESS_ID)).thenReturn(List.of(existingEarn));
        when(stampbookContentService.countTargetContents(STAMPBOOK_ID)).thenReturn(2L);

        useCase.record(VISIT_ID);

        verify(stampEarnService).create(any(StampEarn.class));
        verify(stampbookProgressService).complete(progress, OPERATION_AT);
        verify(stampbookRewardGrantService).create(any());
    }

    @Test
    void record_잠금조회한적립이력이같은방문이면정상무변경한다() {
        StampEarn existingEarn = existingEarn(VISIT_ID, CONTENT_ID + 1);
        when(progress.getStatus()).thenReturn(StampbookProgressStatus.IN_PROGRESS);
        when(stampEarnService.findAllByProgressIdForUpdate(PROGRESS_ID))
            .thenReturn(List.of(existingEarn));

        useCase.record(VISIT_ID);

        verify(stampEarnService, never()).create(any());
        verify(stampbookProgressService, never()).complete(any(), any());
        verify(stampbookRewardGrantService, never()).create(any());
    }

    @Test
    void record_공개전방문이면적립하지않는다() {
        when(visit.getCheckedAt()).thenReturn(OPERATION_AT.minusSeconds(1));
        when(stampbook.getPublishedAt()).thenReturn(OPERATION_AT);

        useCase.record(VISIT_ID);

        verify(stampbookProgressService, never()).findOrCreateForUpdate(any(), any());
        verify(stampEarnService, never()).create(any());
    }

    private StampEarn existingEarn(
        Long existingVisitId,
        Long existingContentId
    ) {
        Visit existingVisit = mock(Visit.class);
        Content existingContent = mock(Content.class);
        StampEarn existingEarn = mock(StampEarn.class);
        when(existingVisit.getVisitId()).thenReturn(existingVisitId);
        when(existingContent.getContentId()).thenReturn(existingContentId);
        when(existingEarn.getVisit()).thenReturn(existingVisit);
        when(existingEarn.getContent()).thenReturn(existingContent);
        return existingEarn;
    }
}
