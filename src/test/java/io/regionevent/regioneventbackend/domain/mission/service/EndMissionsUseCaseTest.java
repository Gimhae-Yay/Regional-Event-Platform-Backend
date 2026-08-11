package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;

class EndMissionsUseCaseTest {

    private static final Long MISSION_ID = 700L;
    private static final Instant OPERATION_AT = Instant.parse("2026-08-11T01:00:00Z");

    private final MissionService missionService = mock(MissionService.class);
    private final MissionParticipationService missionParticipationService = mock(MissionParticipationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase = mock(
        RecordFailedAuditEventUseCase.class
    );
    private final EndMissionsUseCase useCase = new EndMissionsUseCase(
        missionService,
        missionParticipationService,
        recordAuditEventUseCase,
        recordFailedAuditEventUseCase
    );

    @Test
    void endBySystem_종료시각에도달한공개미션을종료하고참여와성공감사를같이처리한다() {
        Mission mission = publishedMission(OPERATION_AT);
        UUID requestId = UUID.randomUUID();
        when(missionService.findForUpdate(MISSION_ID)).thenReturn(mission);
        when(missionService.findCurrentDatabaseTime()).thenReturn(OPERATION_AT);
        when(missionService.end(mission, OPERATION_AT)).thenReturn(mission);

        EndMissionSystemResult result = useCase.endBySystem(MISSION_ID, requestId);

        assertThat(result.status()).isEqualTo(EndMissionSystemResult.Status.ENDED);
        InOrder inOrder = inOrder(missionService, missionParticipationService, recordAuditEventUseCase);
        inOrder.verify(missionService).findForUpdate(MISSION_ID);
        inOrder.verify(missionService).findCurrentDatabaseTime();
        inOrder.verify(missionService).end(mission, OPERATION_AT);
        inOrder.verify(missionParticipationService).endInProgress(MISSION_ID);
        inOrder.verify(recordAuditEventUseCase).record(any(AuditEventCommand.class));
        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(commandCaptor.capture());
        AuditEventCommand command = commandCaptor.getValue();
        assertThat(command.requestId()).isEqualTo(requestId);
        assertThat(command.targetType()).isEqualTo(AuditEventTargetType.MISSION);
        assertThat(command.targetId()).isEqualTo(MISSION_ID);
        assertThat(command.previousState()).isEqualTo(MissionStatus.PUBLISHED.name());
        assertThat(command.nextState()).isEqualTo(MissionStatus.ENDED.name());
        assertThat(command.result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(command.reasonCode()).isEqualTo("MISSION_END_TIME_REACHED");
        assertThat(command.actor()).isNull();
        assertThat(command.occurredAt()).isEqualTo(OPERATION_AT);
    }

    @Test
    void endBySystem_잠금후미션이공개상태가아니면변경하지않는다() {
        Mission mission = mock(Mission.class);
        when(mission.getStatus()).thenReturn(MissionStatus.ENDED);
        when(missionService.findForUpdate(MISSION_ID)).thenReturn(mission);

        EndMissionSystemResult result = useCase.endBySystem(MISSION_ID, UUID.randomUUID());

        assertThat(result.status()).isEqualTo(EndMissionSystemResult.Status.SKIPPED);
        verify(missionService, never()).findCurrentDatabaseTime();
        verifyNoInteractions(
            missionParticipationService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase
        );
    }

    @Test
    void endBySystem_잠금후종료시각전이면변경하지않는다() {
        Mission mission = publishedMission(OPERATION_AT.plusSeconds(1));
        when(missionService.findForUpdate(MISSION_ID)).thenReturn(mission);
        when(missionService.findCurrentDatabaseTime()).thenReturn(OPERATION_AT);

        EndMissionSystemResult result = useCase.endBySystem(MISSION_ID, UUID.randomUUID());

        assertThat(result.status()).isEqualTo(EndMissionSystemResult.Status.SKIPPED);
        verify(missionService, never()).end(any(), any());
        verifyNoInteractions(
            missionParticipationService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase
        );
    }

    @Test
    void endBySystem_종료처리에실패하면실패감사를등록하고예외를전파한다() {
        Mission mission = publishedMission(OPERATION_AT);
        UUID requestId = UUID.randomUUID();
        when(missionService.findForUpdate(MISSION_ID)).thenReturn(mission);
        when(missionService.findCurrentDatabaseTime()).thenReturn(OPERATION_AT);
        when(missionService.end(mission, OPERATION_AT)).thenThrow(new IllegalStateException("종료 실패"));

        assertThatThrownBy(() -> useCase.endBySystem(MISSION_ID, requestId))
            .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordFailedAuditEventUseCase).record(commandCaptor.capture());
        AuditEventCommand command = commandCaptor.getValue();
        assertThat(command.requestId()).isEqualTo(requestId);
        assertThat(command.targetType()).isEqualTo(AuditEventTargetType.MISSION);
        assertThat(command.targetId()).isEqualTo(MISSION_ID);
        assertThat(command.previousState()).isEqualTo(MissionStatus.PUBLISHED.name());
        assertThat(command.nextState()).isNull();
        assertThat(command.result()).isEqualTo(AuditEventResult.FAILURE);
        assertThat(command.reasonCode()).isEqualTo("MISSION_AUTO_END_FAILED");
        assertThat(command.actor()).isNull();
    }

    private Mission publishedMission(Instant endsAt) {
        Mission mission = mock(Mission.class);
        Region region = mock(Region.class);
        when(mission.getMissionId()).thenReturn(MISSION_ID);
        when(mission.getRegion()).thenReturn(region);
        when(mission.getStatus()).thenReturn(MissionStatus.PUBLISHED);
        when(mission.getEndsAt()).thenReturn(endsAt);
        return mission;
    }
}
