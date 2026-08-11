package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.service.MissionHistoryReadResult;
import io.regionevent.regioneventbackend.domain.audit.service.MissionHistoryReadService;
import io.regionevent.regioneventbackend.domain.mission.dto.RegionAdminMissionHistoryResponse;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetRegionAdminMissionHistoryUseCaseTest {

    private final RegionAdminAuthorizationService regionAdminAuthorizationService = mock(
        RegionAdminAuthorizationService.class
    );
    private final MissionService missionService = mock(MissionService.class);
    private final MissionHistoryReadService missionHistoryReadService = mock(MissionHistoryReadService.class);
    private final GetRegionAdminMissionHistoryUseCase useCase = new GetRegionAdminMissionHistoryUseCase(
        regionAdminAuthorizationService,
        missionService,
        missionHistoryReadService
    );

    @Test
    void get_authorizedRegionMission_returnsMappedHistory() {
        Mission mission = missionWithRegion(11L);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(100L)).thenReturn(11L);
        when(missionService.findMissionDetail(701L)).thenReturn(mission);
        when(missionHistoryReadService.findAll(701L)).thenReturn(List.of(new MissionHistoryReadResult(
            12001L,
            "SUBMITTED",
            "DRAFT",
            "PENDING_REVIEW",
            AuditEventResult.SUCCESS,
            "MISSION_SUBMITTED",
            "USER",
            31L,
            Instant.parse("2026-08-07T04:20:00Z")
        )));

        RegionAdminMissionHistoryResponse response = useCase.get(100L, 701L);

        assertThat(response.missionId()).isEqualTo("701");
        assertThat(response.histories()).singleElement().satisfies(history -> {
            assertThat(history.auditEventId()).isEqualTo("12001");
            assertThat(history.action()).isEqualTo("SUBMITTED");
            assertThat(history.actorUserId()).isEqualTo("31");
        });
        verify(regionAdminAuthorizationService).requireAuthorizedRegionId(100L);
        verify(missionService).findMissionDetail(701L);
        verify(missionHistoryReadService).findAll(701L);
    }

    @Test
    void get_differentMissionRegion_throwsForbiddenWithoutReadingHistory() {
        Mission mission = missionWithRegion(12L);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(100L)).thenReturn(11L);
        when(missionService.findMissionDetail(701L)).thenReturn(mission);

        assertThatThrownBy(() -> useCase.get(100L, 701L))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
        verifyNoInteractions(missionHistoryReadService);
    }

    private Mission missionWithRegion(Long regionId) {
        Region region = mock(Region.class);
        Mission mission = mock(Mission.class);
        when(region.getRegionId()).thenReturn(regionId);
        when(mission.getRegion()).thenReturn(region);
        return mission;
    }
}
