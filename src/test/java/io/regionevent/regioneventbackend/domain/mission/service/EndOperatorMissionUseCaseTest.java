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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class EndOperatorMissionUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 11L;
    private static final Long MISSION_ID = 701L;
    private static final String REASON_CODE = "MISSION_OPERATION_SCHEDULE_CHANGED";
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant ENDED_AT = Instant.parse("2026-08-11T04:30:00.123456Z");

    private final OperatorAuthorizationService authorizationService =
        mock(OperatorAuthorizationService.class);
    private final MissionService missionService = mock(MissionService.class);
    private final MissionParticipationService participationService =
        mock(MissionParticipationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase =
        mock(RecordAuditEventUseCase.class);
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase =
        mock(RecordFailedAuditEventUseCase.class);
    private final EndOperatorMissionUseCase useCase = new EndOperatorMissionUseCase(
        authorizationService,
        missionService,
        participationService,
        recordAuditEventUseCase,
        recordFailedAuditEventUseCase,
        Clock.fixed(ENDED_AT, ZoneOffset.UTC)
    );

    private Region region;
    private Mission mission;
    private AuthorizedOperator operator;

    @BeforeEach
    void setUp() {
        region = mock(Region.class);
        when(region.getRegionId()).thenReturn(REGION_ID);
        AppUser user = mock(AppUser.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        when(assignment.getRoleAssignmentId()).thenReturn(900L);
        when(assignment.getAppUser()).thenReturn(user);
        when(assignment.getRole()).thenReturn(UserRole.OPERATOR);
        operator = new AuthorizedOperator(user, region, assignment);

        mission = mock(Mission.class);
        when(mission.getMissionId()).thenReturn(MISSION_ID);
        when(mission.getRegion()).thenReturn(region);
        when(mission.getStatus()).thenReturn(
            MissionStatus.PUBLISHED,
            MissionStatus.PUBLISHED,
            MissionStatus.ENDED
        );
        when(mission.getEndedAt()).thenReturn(ENDED_AT);

        when(authorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(operator);
        when(missionService.findForUpdate(MISSION_ID)).thenReturn(mission);
        when(missionService.end(mission, ENDED_AT)).thenReturn(mission);
    }

    @Test
    void end_publishedMission_locksAndChangesInContractOrderAndRecordsSuccess() {
        EndOperatorMissionResult result = useCase.end(USER_ID, MISSION_ID, REASON_CODE, REQUEST_ID);

        assertThat(result.missionId()).isEqualTo(MISSION_ID);
        assertThat(result.status()).isEqualTo(MissionStatus.ENDED);
        assertThat(result.endedAt()).isEqualTo(ENDED_AT);
        InOrder order = inOrder(
            authorizationService,
            missionService,
            participationService,
            recordAuditEventUseCase
        );
        order.verify(authorizationService).requireAuthorizedOperator(USER_ID);
        order.verify(missionService).findForUpdate(MISSION_ID);
        order.verify(participationService).endInProgress(MISSION_ID);
        order.verify(missionService).end(mission, ENDED_AT);
        order.verify(recordAuditEventUseCase).record(any(AuditEventCommand.class));
        verifyNoInteractions(recordFailedAuditEventUseCase);

        ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(captor.capture());
        AuditEventCommand command = captor.getValue();
        assertThat(command.targetType()).isEqualTo(AuditEventTargetType.MISSION);
        assertThat(command.targetId()).isEqualTo(MISSION_ID);
        assertThat(command.previousState()).isEqualTo("PUBLISHED");
        assertThat(command.nextState()).isEqualTo("ENDED");
        assertThat(command.result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(command.reasonCode()).isEqualTo(REASON_CODE);
        assertThat(command.occurredAt()).isEqualTo(ENDED_AT);
    }

    @Test
    void end_unsupportedReason_rejectsBeforeAuthorizationAndLookup() {
        assertThatThrownBy(() -> useCase.end(USER_ID, MISSION_ID, " MISSION_OPERATION_SCHEDULE_CHANGED", REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );

        verifyNoInteractions(
            authorizationService,
            missionService,
            participationService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase
        );
    }

    @Test
    void end_otherRegion_recordsForbiddenFailureWithoutParticipationChange() {
        Region otherRegion = mock(Region.class);
        when(otherRegion.getRegionId()).thenReturn(22L);
        when(mission.getRegion()).thenReturn(otherRegion);

        assertBusinessError(ErrorCode.FORBIDDEN);

        verifyNoInteractions(participationService, recordAuditEventUseCase);
        assertFailureAudit(ErrorCode.FORBIDDEN);
    }

    @Test
    void end_nonPublishedMission_recordsStateConflictFailure() {
        when(mission.getStatus()).thenReturn(MissionStatus.DRAFT);

        assertBusinessError(ErrorCode.MISSION_STATE_CONFLICT);

        verifyNoInteractions(participationService, recordAuditEventUseCase);
        assertFailureAudit(ErrorCode.MISSION_STATE_CONFLICT, "DRAFT");
    }

    @Test
    void end_participationFailure_recordsInternalFailure() {
        org.mockito.Mockito.doThrow(new IllegalStateException("participation failure"))
            .when(participationService)
            .endInProgress(MISSION_ID);

        assertThatThrownBy(() -> useCase.end(USER_ID, MISSION_ID, REASON_CODE, REQUEST_ID))
            .isInstanceOf(IllegalStateException.class);

        verify(missionService, never()).end(mission, ENDED_AT);
        verifyNoInteractions(recordAuditEventUseCase);
        assertFailureAudit(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    void end_unknownMission_doesNotRecordFailureAudit() {
        when(missionService.findForUpdate(MISSION_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertBusinessError(ErrorCode.NOT_FOUND);

        verifyNoInteractions(participationService, recordAuditEventUseCase, recordFailedAuditEventUseCase);
    }

    private void assertBusinessError(ErrorCode errorCode) {
        assertThatThrownBy(() -> useCase.end(USER_ID, MISSION_ID, REASON_CODE, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(errorCode)
            );
    }

    private void assertFailureAudit(ErrorCode errorCode) {
        assertFailureAudit(errorCode, "PUBLISHED");
    }

    private void assertFailureAudit(
        ErrorCode errorCode,
        String previousState
    ) {
        ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordFailedAuditEventUseCase).record(captor.capture());
        AuditEventCommand command = captor.getValue();
        assertThat(command.requestId()).isEqualTo(REQUEST_ID);
        assertThat(command.targetId()).isEqualTo(MISSION_ID);
        assertThat(command.previousState()).isEqualTo(previousState);
        assertThat(command.nextState()).isNull();
        assertThat(command.result()).isEqualTo(AuditEventResult.FAILURE);
        assertThat(command.reasonCode()).isEqualTo(errorCode.code());
    }
}
