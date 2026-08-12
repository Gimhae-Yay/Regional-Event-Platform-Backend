package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.MissionParticipationReadService.MissionProgressCandidate;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;

class RecordMissionProgressUseCaseTest {

    private static final Long VISIT_ID = 10L;
    private static final Long USER_ID = 20L;
    private static final Long REGION_ID = 30L;
    private static final Long CONTENT_ID = 40L;
    private static final Long MISSION_ID = 50L;
    private static final Long PARTICIPATION_ID = 60L;
    private static final Instant OPERATION_AT = Instant.parse("2026-08-11T10:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000618");

    private final VisitService visitService = mock(VisitService.class);
    private final MissionParticipationReadService participationReadService = mock(
        MissionParticipationReadService.class
    );
    private final MissionService missionService = mock(MissionService.class);
    private final MissionParticipationService participationService = mock(MissionParticipationService.class);
    private final MissionTargetContentService targetContentService = mock(MissionTargetContentService.class);
    private final MissionProgressService progressService = mock(MissionProgressService.class);
    private final MissionProgressDuplicateReadService duplicateReadService = mock(
        MissionProgressDuplicateReadService.class
    );
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final RecordMissionProgressUseCase useCase = new RecordMissionProgressUseCase(
        visitService,
        participationReadService,
        missionService,
        participationService,
        targetContentService,
        progressService,
        duplicateReadService,
        transactionManager
    );

    private final Visit visit = mock(Visit.class);
    private final AppUser user = mock(AppUser.class);
    private final Region region = mock(Region.class);
    private final Content content = mock(Content.class);
    private final Mission mission = mock(Mission.class);
    private final MissionParticipation participation = mock(MissionParticipation.class);
    private final MissionProgressCandidate candidate = new MissionProgressCandidate(PARTICIPATION_ID, MISSION_ID);

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
            .thenReturn(mock(TransactionStatus.class));
        when(visit.getUser()).thenReturn(user);
        when(user.getUserId()).thenReturn(USER_ID);
        when(visit.getRegion()).thenReturn(region);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(visit.getContent()).thenReturn(content);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(visit.getCheckedAt()).thenReturn(OPERATION_AT.minusSeconds(1));
        when(visitService.findMissionProgressSource(VISIT_ID)).thenReturn(Optional.of(visit));
        when(participationReadService.findProgressCandidates(USER_ID, REGION_ID))
            .thenReturn(List.of(candidate));
        when(missionService.findMissionForParticipationUpdate(MISSION_ID)).thenReturn(mission);
        when(participationService.findByIdForProgressUpdate(PARTICIPATION_ID))
            .thenReturn(Optional.of(participation));
        when(missionService.findCurrentDatabaseTime()).thenReturn(OPERATION_AT);
        when(visitService.findMissionProgressSourceInCurrentTransaction(VISIT_ID))
            .thenReturn(Optional.of(visit));
        when(mission.getMissionId()).thenReturn(MISSION_ID);
        when(mission.getRegion()).thenReturn(region);
        when(mission.getStatus()).thenReturn(MissionStatus.PUBLISHED);
        when(mission.getEndsAt()).thenReturn(OPERATION_AT.plusSeconds(1));
        when(participation.getMissionParticipationId()).thenReturn(PARTICIPATION_ID);
        when(participation.getMission()).thenReturn(mission);
        when(participation.getUser()).thenReturn(user);
        when(participation.getStatus()).thenReturn(MissionParticipationStatus.IN_PROGRESS);
        when(participation.getJoinedAt()).thenReturn(OPERATION_AT.minusSeconds(2));
    }

    @Test
    void record_방문이없으면정상무변경한다() {
        when(visitService.findMissionProgressSource(VISIT_ID)).thenReturn(Optional.empty());

        useCase.record(VISIT_ID, REQUEST_ID);

        verifyNoInteractions(participationReadService, missionService, participationService, progressService);
    }

    @Test
    void record_방문횟수목표를충족하면잠금후단일DB시각으로근거와완료를기록한다() {
        when(mission.getConditionType()).thenReturn(MissionConditionType.VISIT_COUNT);
        when(mission.getRequiredVisitCount()).thenReturn(1);
        when(progressService.countByParticipationId(PARTICIPATION_ID)).thenReturn(1L);

        useCase.record(VISIT_ID, REQUEST_ID);

        InOrder inOrder = inOrder(missionService, participationService, visitService, progressService);
        inOrder.verify(missionService).findMissionForParticipationUpdate(MISSION_ID);
        inOrder.verify(participationService).findByIdForProgressUpdate(PARTICIPATION_ID);
        inOrder.verify(missionService).findCurrentDatabaseTime();
        inOrder.verify(visitService).findMissionProgressSourceInCurrentTransaction(VISIT_ID);
        inOrder.verify(progressService).create(any());
        inOrder.verify(participationService).complete(participation, OPERATION_AT);
        verify(missionService).findCurrentDatabaseTime();
    }

    @Test
    void record_콘텐츠집합에서이미반영한콘텐츠면정상무변경한다() {
        when(mission.getConditionType()).thenReturn(MissionConditionType.CONTENT_SET);
        when(targetContentService.contains(MISSION_ID, CONTENT_ID)).thenReturn(true);
        when(progressService.existsByContentId(PARTICIPATION_ID, CONTENT_ID)).thenReturn(true);

        useCase.record(VISIT_ID, REQUEST_ID);

        verify(progressService, never()).create(any());
        verify(participationService, never()).complete(any(), any());
    }

    @Test
    void record_콘텐츠집합목표를충족하면근거와완료를같은대상트랜잭션에서기록한다() {
        when(mission.getConditionType()).thenReturn(MissionConditionType.CONTENT_SET);
        when(targetContentService.contains(MISSION_ID, CONTENT_ID)).thenReturn(true);
        when(progressService.countByParticipationId(PARTICIPATION_ID)).thenReturn(1L);
        when(targetContentService.countRequiredContents(MISSION_ID)).thenReturn(1L);

        useCase.record(VISIT_ID, REQUEST_ID);

        verify(progressService).create(any());
        verify(participationService).complete(participation, OPERATION_AT);
    }

    @Test
    void record_콘텐츠집합대상이아닌콘텐츠면정상무변경한다() {
        when(mission.getConditionType()).thenReturn(MissionConditionType.CONTENT_SET);
        when(targetContentService.contains(MISSION_ID, CONTENT_ID)).thenReturn(false);

        useCase.record(VISIT_ID, REQUEST_ID);

        verify(progressService, never()).create(any());
    }

    @Test
    void record_참여시각전방문이면정상무변경한다() {
        when(participation.getJoinedAt()).thenReturn(OPERATION_AT);

        useCase.record(VISIT_ID, REQUEST_ID);

        verify(progressService, never()).create(any());
    }

    @Test
    void record_미션종료시각과DB시각이같으면정상무변경한다() {
        when(mission.getEndsAt()).thenReturn(OPERATION_AT);

        useCase.record(VISIT_ID, REQUEST_ID);

        verify(progressService, never()).create(any());
    }

    @Test
    void record_미공개미션이면정상무변경한다() {
        when(mission.getStatus()).thenReturn(MissionStatus.DRAFT);

        useCase.record(VISIT_ID, REQUEST_ID);

        verify(progressService, never()).create(any());
    }

    @Test
    void record_방문과참여사용자가다르면정상무변경한다() {
        AppUser anotherUser = mock(AppUser.class);
        when(anotherUser.getUserId()).thenReturn(USER_ID + 1);
        when(participation.getUser()).thenReturn(anotherUser);

        useCase.record(VISIT_ID, REQUEST_ID);

        verify(progressService, never()).create(any());
    }

    @Test
    void record_방문과미션지역이다르면정상무변경한다() {
        Region anotherRegion = mock(Region.class);
        when(anotherRegion.getRegionId()).thenReturn(REGION_ID + 1);
        when(mission.getRegion()).thenReturn(anotherRegion);

        useCase.record(VISIT_ID, REQUEST_ID);

        verify(progressService, never()).create(any());
    }

    @Test
    void record_동일방문근거가이미있으면정상무변경한다() {
        when(progressService.existsByVisitId(PARTICIPATION_ID, VISIT_ID)).thenReturn(true);

        useCase.record(VISIT_ID, REQUEST_ID);

        verify(progressService, never()).create(any());
    }

    @Test
    void record_동일방문제약충돌후승자근거가있으면현재결과로수렴한다() {
        when(mission.getConditionType()).thenReturn(MissionConditionType.VISIT_COUNT);
        when(progressService.create(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(duplicateReadService.exists(PARTICIPATION_ID, VISIT_ID)).thenReturn(true);

        useCase.record(VISIT_ID, REQUEST_ID);

        verify(duplicateReadService).exists(PARTICIPATION_ID, VISIT_ID);
        verify(participationService, never()).complete(any(), any());
    }

    @Test
    void record_참여실패는허용식별자와비개인오류코드만기록한다() {
        when(missionService.findMissionForParticipationUpdate(MISSION_ID))
            .thenThrow(new IllegalStateException("visitor@example.com token-secret visit-detail"));
        Logger logger = (Logger) LoggerFactory.getLogger(RecordMissionProgressUseCase.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            useCase.record(VISIT_ID, REQUEST_ID);

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).isEqualTo("Mission progress processing failed");
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getKeyValuePairs())
                    .extracting(pair -> pair.key, pair -> pair.value)
                    .containsExactly(
                        tuple("requestId", REQUEST_ID),
                        tuple("visitId", VISIT_ID),
                        tuple("missionId", MISSION_ID),
                        tuple("missionParticipationId", PARTICIPATION_ID),
                        tuple("errorCode", "MISSION_PROGRESS_TARGET_PROCESSING_FAILED")
                    );
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
