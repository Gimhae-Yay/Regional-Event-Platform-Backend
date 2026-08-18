package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

class GetRegionAdminMissionsUseCaseTest {

    private final RegionAdminAuthorizationService regionAdminAuthorizationService = mock(
        RegionAdminAuthorizationService.class
    );
    private final MissionService missionService = mock(MissionService.class);
    private final GetRegionAdminMissionsUseCase useCase = new GetRegionAdminMissionsUseCase(
        regionAdminAuthorizationService,
        missionService
    );

    @Test
    void get_withPendingReviewStatus_usesAuthorizedRegionAndRequestedPage() {
        Mission mission = mock(Mission.class);
        when(mission.getMissionId()).thenReturn(701L);
        when(mission.getTitle()).thenReturn("김해 문화 미션");
        when(mission.getStatus()).thenReturn(MissionStatus.PENDING_REVIEW);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(100L)).thenReturn(11L);
        when(missionService.findRegionMissions(
            11L,
            MissionStatus.PENDING_REVIEW,
            PageRequest.of(1, 10)
        )).thenReturn(new PageImpl<>(List.of(mission), PageRequest.of(1, 10), 11));

        RegionAdminMissionListResult result = useCase.get(100L, MissionStatus.PENDING_REVIEW, 1, 10);

        assertThat(result.content()).containsExactly(
            new RegionAdminMissionListResult.MissionSummary(
                701L,
                "김해 문화 미션",
                MissionStatus.PENDING_REVIEW
            )
        );
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(11);
        verify(regionAdminAuthorizationService).requireAuthorizedRegionId(100L);
        verify(missionService).findRegionMissions(11L, MissionStatus.PENDING_REVIEW, PageRequest.of(1, 10));
    }

    @Test
    void get_withoutStatus_usesAllStatusQuery() {
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(100L)).thenReturn(11L);
        when(missionService.findRegionMissions(11L, null, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        RegionAdminMissionListResult result = useCase.get(100L, null, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalPages()).isZero();
        verify(missionService).findRegionMissions(11L, null, PageRequest.of(0, 20));
    }
}
