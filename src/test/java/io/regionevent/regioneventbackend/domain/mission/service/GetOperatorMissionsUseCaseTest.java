package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;

class GetOperatorMissionsUseCaseTest {

    private static final Instant ENDS_AT = Instant.parse("2026-09-30T14:59:59Z");

    private final OperatorAuthorizationService operatorAuthorizationService = mock(
        OperatorAuthorizationService.class
    );
    private final MissionService missionService = mock(MissionService.class);
    private final GetOperatorMissionsUseCase useCase = new GetOperatorMissionsUseCase(
        operatorAuthorizationService,
        missionService
    );

    @Test
    void get_withStatus_usesAuthorizedOperatorRegionAndRequestedPage() {
        AuthorizedOperator operator = mock(AuthorizedOperator.class);
        Region region = mock(Region.class);
        Mission mission = mock(Mission.class);
        when(operatorAuthorizationService.requireAuthorizedOperator(100L)).thenReturn(operator);
        when(operator.region()).thenReturn(region);
        when(region.getRegionId()).thenReturn(11L);
        when(mission.getMissionId()).thenReturn(701L);
        when(mission.getStatus()).thenReturn(MissionStatus.PUBLISHED);
        when(mission.getConditionType()).thenReturn(MissionConditionType.CONTENT_SET);
        when(mission.getEndsAt()).thenReturn(ENDS_AT);
        when(missionService.findRegionMissions(11L, MissionStatus.PUBLISHED, PageRequest.of(1, 10)))
            .thenReturn(new PageImpl<>(List.of(mission), PageRequest.of(1, 10), 11));

        OperatorMissionListResult result = useCase.get(100L, MissionStatus.PUBLISHED, 1, 10);

        assertThat(result.content()).containsExactly(new OperatorMissionListResult.MissionSummary(
            701L,
            MissionStatus.PUBLISHED,
            MissionConditionType.CONTENT_SET,
            ENDS_AT
        ));
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(11);
        assertThat(result.totalPages()).isEqualTo(2);
        verify(operatorAuthorizationService).requireAuthorizedOperator(100L);
        verify(missionService).findRegionMissions(11L, MissionStatus.PUBLISHED, PageRequest.of(1, 10));
    }

    @Test
    void get_withoutStatus_usesAllStatusQueryAndReturnsEmptyPageMetadata() {
        AuthorizedOperator operator = mock(AuthorizedOperator.class);
        Region region = mock(Region.class);
        when(operatorAuthorizationService.requireAuthorizedOperator(100L)).thenReturn(operator);
        when(operator.region()).thenReturn(region);
        when(region.getRegionId()).thenReturn(11L);
        when(missionService.findRegionMissions(11L, null, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        OperatorMissionListResult result = useCase.get(100L, null, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        verify(missionService).findRegionMissions(11L, null, PageRequest.of(0, 20));
    }
}
