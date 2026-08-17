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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class CreateMissionParticipationUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long MISSION_ID = 701L;
    private static final Long REGION_ID = 10L;
    private static final Instant OPERATION_AT = Instant.parse("2026-08-07T05:00:00Z");

    private final MissionParticipationDuplicateReadService duplicateReadService = mock(
        MissionParticipationDuplicateReadService.class
    );
    private final AppUserService appUserService = mock(AppUserService.class);
    private final UserRoleAssignmentService userRoleAssignmentService = mock(UserRoleAssignmentService.class);
    private final MissionService missionService = mock(MissionService.class);
    private final RegionService regionService = mock(RegionService.class);
    private final MissionParticipationReadService participationReadService = mock(
        MissionParticipationReadService.class
    );
    private final MissionParticipationService participationService = mock(MissionParticipationService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final CreateMissionParticipationUseCase useCase = new CreateMissionParticipationUseCase(
        duplicateReadService,
        appUserService,
        userRoleAssignmentService,
        missionService,
        regionService,
        participationReadService,
        participationService,
        transactionManager
    );

    private final AppUser user = mock(AppUser.class);
    private final UserRoleAssignment visitor = mock(UserRoleAssignment.class);
    private final Mission initialMission = mock(Mission.class);
    private final Region initialRegion = mock(Region.class);

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
            .thenReturn(mock(TransactionStatus.class));
        when(appUserService.findActiveUserForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(userRoleAssignmentService.findActiveVisitor(USER_ID)).thenReturn(visitor);
        when(visitor.getAppUser()).thenReturn(user);
        when(missionService.findMission(MISSION_ID)).thenReturn(initialMission);
        when(initialMission.getRegion()).thenReturn(initialRegion);
        when(initialRegion.isPublic()).thenReturn(true);
        when(participationReadService.findByMissionIdAndUserId(MISSION_ID, USER_ID))
            .thenReturn(Optional.empty());
    }

    @Test
    void create_기존참여가있으면현재미션상태와무관하게기존결과를반환한다() {
        MissionParticipation existingParticipation = participation(
            9001L,
            MissionParticipationStatus.COMPLETED,
            OPERATION_AT.minusSeconds(60)
        );
        when(participationReadService.findByMissionIdAndUserId(MISSION_ID, USER_ID))
            .thenReturn(Optional.of(existingParticipation));

        CreateMissionParticipationResult result = useCase.create(USER_ID, MISSION_ID);

        assertThat(result.participationId()).isEqualTo(9001L);
        assertThat(result.status()).isEqualTo(MissionParticipationStatus.COMPLETED);
        assertThat(result.joinedAt()).isEqualTo(OPERATION_AT.minusSeconds(60));
        verifyNoInteractions(appUserService);
        verify(missionService, never()).findMissionForParticipationUpdate(any());
        verifyNoInteractions(regionService, participationService, duplicateReadService);
    }

    @Test
    void create_비공개지역이면기존참여를조회하지않고찾을수없음으로처리한다() {
        when(initialRegion.isPublic()).thenReturn(false);

        assertThatThrownBy(() -> useCase.create(USER_ID, MISSION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );

        verifyNoInteractions(participationReadService, regionService, participationService);
    }

    @Test
    void create_신규참여이면사용자와미션과지역을순서대로잠그고단일DB시각으로검증하고생성한다() {
        Mission lockedMission = publishedMission(OPERATION_AT.plusSeconds(1));
        Region lockedRegion = mock(Region.class);
        MissionParticipation createdParticipation = participation(
            9001L,
            MissionParticipationStatus.IN_PROGRESS,
            OPERATION_AT
        );
        when(lockedRegion.isPublic()).thenReturn(true);
        when(missionService.findMissionForParticipationUpdate(MISSION_ID)).thenReturn(lockedMission);
        when(regionService.findRegionForUpdate(REGION_ID)).thenReturn(lockedRegion);
        when(missionService.findCurrentDatabaseTime()).thenReturn(OPERATION_AT);
        when(participationService.create(lockedMission, user, OPERATION_AT)).thenReturn(createdParticipation);

        CreateMissionParticipationResult result = useCase.create(USER_ID, MISSION_ID);

        assertThat(result.participationId()).isEqualTo(9001L);
        assertThat(result.joinedAt()).isEqualTo(OPERATION_AT);
        InOrder inOrder = inOrder(appUserService, missionService, regionService, participationService);
        inOrder.verify(appUserService).findActiveUserForUpdate(USER_ID);
        inOrder.verify(missionService).findMissionForParticipationUpdate(MISSION_ID);
        inOrder.verify(regionService).findRegionForUpdate(REGION_ID);
        inOrder.verify(missionService).findCurrentDatabaseTime();
        inOrder.verify(participationService).create(lockedMission, user, OPERATION_AT);
        verify(missionService).findCurrentDatabaseTime();
    }

    @Test
    void create_비활성또는삭제사용자이면도메인잠금과변경없이금지한다() {
        when(appUserService.findActiveUserForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.create(USER_ID, MISSION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(missionService, never()).findMissionForParticipationUpdate(any());
        verifyNoInteractions(regionService, participationService, duplicateReadService);
    }

    @Test
    void create_미공개미션이면상태충돌로처리한다() {
        Mission lockedMission = lockedMission(MissionStatus.DRAFT, OPERATION_AT.plusSeconds(1));
        Region lockedRegion = mock(Region.class);
        when(lockedRegion.isPublic()).thenReturn(true);
        when(missionService.findMissionForParticipationUpdate(MISSION_ID)).thenReturn(lockedMission);
        when(regionService.findRegionForUpdate(REGION_ID)).thenReturn(lockedRegion);
        when(missionService.findCurrentDatabaseTime()).thenReturn(OPERATION_AT);

        assertMissionStateConflict();
    }

    @Test
    void create_종료시각과DB현재시각이같으면상태충돌로처리한다() {
        Mission lockedMission = publishedMission(OPERATION_AT);
        Region lockedRegion = mock(Region.class);
        when(lockedRegion.isPublic()).thenReturn(true);
        when(missionService.findMissionForParticipationUpdate(MISSION_ID)).thenReturn(lockedMission);
        when(regionService.findRegionForUpdate(REGION_ID)).thenReturn(lockedRegion);
        when(missionService.findCurrentDatabaseTime()).thenReturn(OPERATION_AT);

        assertMissionStateConflict();
    }

    @Test
    void create_중복키충돌이면롤백후새트랜잭션에서승자참여를반환한다() {
        Mission lockedMission = publishedMission(OPERATION_AT.plusSeconds(1));
        Region lockedRegion = mock(Region.class);
        MissionParticipation winner = participation(
            9001L,
            MissionParticipationStatus.IN_PROGRESS,
            OPERATION_AT
        );
        when(lockedRegion.isPublic()).thenReturn(true);
        when(missionService.findMissionForParticipationUpdate(MISSION_ID)).thenReturn(lockedMission);
        when(regionService.findRegionForUpdate(REGION_ID)).thenReturn(lockedRegion);
        when(missionService.findCurrentDatabaseTime()).thenReturn(OPERATION_AT);
        when(participationService.create(lockedMission, user, OPERATION_AT))
            .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(duplicateReadService.find(MISSION_ID, USER_ID)).thenReturn(Optional.of(winner));

        CreateMissionParticipationResult result = useCase.create(USER_ID, MISSION_ID);

        assertThat(result.participationId()).isEqualTo(9001L);
        InOrder inOrder = inOrder(transactionManager, duplicateReadService);
        inOrder.verify(transactionManager).rollback(any(TransactionStatus.class));
        inOrder.verify(duplicateReadService).find(MISSION_ID, USER_ID);
    }

    @Test
    void create_중복키복구에서지역이비공개면찾을수없음오류를유지한다() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(duplicateReadService.find(MISSION_ID, USER_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> useCase.create(USER_ID, MISSION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );
    }

    private void assertMissionStateConflict() {
        assertThatThrownBy(() -> useCase.create(USER_ID, MISSION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MISSION_STATE_CONFLICT)
            );
        verify(participationService, never()).create(any(), any(), any());
    }

    private Mission publishedMission(Instant endsAt) {
        return lockedMission(MissionStatus.PUBLISHED, endsAt);
    }

    private Mission lockedMission(
        MissionStatus status,
        Instant endsAt
    ) {
        Mission mission = mock(Mission.class);
        Region region = mock(Region.class);
        when(mission.getRegion()).thenReturn(region);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(mission.getStatus()).thenReturn(status);
        when(mission.getEndsAt()).thenReturn(endsAt);
        return mission;
    }

    private MissionParticipation participation(
        Long participationId,
        MissionParticipationStatus status,
        Instant joinedAt
    ) {
        Mission mission = mock(Mission.class);
        MissionParticipation participation = mock(MissionParticipation.class);
        when(mission.getMissionId()).thenReturn(MISSION_ID);
        when(participation.getMissionParticipationId()).thenReturn(participationId);
        when(participation.getMission()).thenReturn(mission);
        when(participation.getStatus()).thenReturn(status);
        when(participation.getJoinedAt()).thenReturn(joinedAt);
        return participation;
    }
}
