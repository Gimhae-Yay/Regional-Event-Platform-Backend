package io.regionevent.regioneventbackend.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.region.entity.Region;

class GetPublicRegionsUseCaseTest {

    private final RegionService regionService = mock(RegionService.class);
    private final PublicRegionCacheAside publicRegionCacheAside = mock(PublicRegionCacheAside.class);
    private final GetPublicRegionsUseCase useCase = new GetPublicRegionsUseCase(
        regionService,
        publicRegionCacheAside
    );

    @Test
    void get_MySQL에서_공개_여부를_확인한_뒤_캐시_정적_정보로_응답을_조립한다() {
        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(10L);
        when(region.getRegionCode()).thenReturn("GIMHAE");
        when(region.getName()).thenReturn("MySQL 김해시");
        PublicRegionStaticInfo cached = new PublicRegionStaticInfo(10L, "GIMHAE", "캐시 김해시");
        when(regionService.findPublicRegions()).thenReturn(List.of(region));
        when(publicRegionCacheAside.resolve(any(PublicRegionStaticInfo.class))).thenReturn(cached);

        List<PublicRegionStaticInfo> result = useCase.get();

        assertThat(result).containsExactly(cached);
        verify(regionService).findPublicRegions();
        verify(publicRegionCacheAside).resolve(
            new PublicRegionStaticInfo(10L, "GIMHAE", "MySQL 김해시")
        );
    }
}
