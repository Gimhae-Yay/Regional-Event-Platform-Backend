package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;

@ExtendWith(MockitoExtension.class)
class GetMyMissionParticipationsUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Instant JOINED_AT = Instant.parse("2026-08-07T05:00:00Z");

    @Mock
    private UserRoleAssignmentService userRoleAssignmentService;

    @Mock
    private MissionParticipationReadService missionParticipationReadService;

    @InjectMocks
    private GetMyMissionParticipationsUseCase getMyMissionParticipationsUseCase;

    @Test
    void get_활성방문자와상태필터_본인참여페이지를반환한다() {
        UserRoleAssignment visitor = mock(UserRoleAssignment.class);
        AppUser user = mock(AppUser.class);
        MissionParticipationSummary summary = new MissionParticipationSummary(
            9001L,
            701L,
            MissionParticipationStatus.IN_PROGRESS,
            1,
            3,
            false,
            JOINED_AT,
            null
        );
        PageRequest pageable = PageRequest.of(1, 2);
        when(userRoleAssignmentService.findActiveVisitor(USER_ID)).thenReturn(visitor);
        when(visitor.getAppUser()).thenReturn(user);
        when(user.getUserId()).thenReturn(USER_ID);
        when(missionParticipationReadService.findByUserIdAndStatus(
            USER_ID,
            MissionParticipationStatus.IN_PROGRESS,
            pageable
        )).thenReturn(new PageImpl<>(List.of(summary), pageable, 3));

        MyMissionParticipationListResult result = getMyMissionParticipationsUseCase.get(
            USER_ID,
            MissionParticipationStatus.IN_PROGRESS,
            1,
            2
        );

        assertThat(result.content()).containsExactly(new MyMissionParticipationListResult.Participation(
            9001L,
            701L,
            MissionParticipationStatus.IN_PROGRESS,
            1,
            3,
            false,
            JOINED_AT,
            null
        ));
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
        verify(userRoleAssignmentService).findActiveVisitor(USER_ID);
        verify(missionParticipationReadService).findByUserIdAndStatus(
            USER_ID,
            MissionParticipationStatus.IN_PROGRESS,
            pageable
        );
    }

    @Test
    void get_필터없고빈결과_빈페이지메타데이터를반환한다() {
        UserRoleAssignment visitor = mock(UserRoleAssignment.class);
        AppUser user = mock(AppUser.class);
        PageRequest pageable = PageRequest.of(0, 20);
        when(userRoleAssignmentService.findActiveVisitor(USER_ID)).thenReturn(visitor);
        when(visitor.getAppUser()).thenReturn(user);
        when(user.getUserId()).thenReturn(USER_ID);
        when(missionParticipationReadService.findByUserIdAndStatus(USER_ID, null, pageable))
            .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        MyMissionParticipationListResult result = getMyMissionParticipationsUseCase.get(
            USER_ID,
            null,
            0,
            20
        );

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }
}
