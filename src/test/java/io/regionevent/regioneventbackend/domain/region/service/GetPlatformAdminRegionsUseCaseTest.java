package io.regionevent.regioneventbackend.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.region.repository.PlatformAdminRegionListProjection;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetPlatformAdminRegionsUseCaseTest {

    private static final Long ACTOR_USER_ID = 101L;

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService = mock(
        PlatformAdminAuthorizationService.class
    );
    private final RegionService regionService = mock(RegionService.class);
    private final GetPlatformAdminRegionsUseCase useCase = new GetPlatformAdminRegionsUseCase(
        platformAdminAuthorizationService,
        regionService
    );

    @Test
    void get_활성전체관리자_필터조건의지역목록을반환한다() {
        PlatformAdminRegionListProjection projection = new PlatformAdminRegionListProjection(
            11L,
            "GIMHAE",
            "김해시",
            false,
            2L,
            Instant.parse("2026-08-09T00:00:00Z"),
            Instant.parse("2026-08-09T01:00:00Z")
        );
        when(regionService.findPlatformAdminRegionList(false)).thenReturn(List.of(projection));

        List<PlatformAdminRegionListInfo> result = useCase.get(ACTOR_USER_ID, false);

        assertThat(result).containsExactly(PlatformAdminRegionListInfo.from(projection));
        verify(platformAdminAuthorizationService).requireAuthorizedPlatformAdmin(ACTOR_USER_ID);
        verify(regionService).findPlatformAdminRegionList(false);
    }

    @Test
    void get_고권한인가에실패하면_지역을조회하지않는다() {
        when(platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> useCase.get(ACTOR_USER_ID, null))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(regionService, never()).findPlatformAdminRegionList(null);
    }
}
