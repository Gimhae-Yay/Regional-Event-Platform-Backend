package io.regionevent.regioneventbackend.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.region.repository.PublicRegionVerificationProjection;

class GetPublicRegionsUseCaseTest {

    private final RegionService regionService = mock(RegionService.class);
    private final PublicRegionCache publicRegionCache = mock(PublicRegionCache.class);
    private final PublicRegionCacheAside publicRegionCacheAside = new PublicRegionCacheAside(publicRegionCache);
    private final GetPublicRegionsUseCase useCase = new GetPublicRegionsUseCase(
        regionService,
        publicRegionCacheAside
    );

    @Test
    void get_캐시_적중이면_정적_표시_정보를_MySQL에서_추가_조회하지_않는다() {
        PublicRegionStaticInfo cached = new PublicRegionStaticInfo(10L, "GIMHAE", "캐시 김해시");
        when(regionService.findPublicRegionVerifications()).thenReturn(
            List.of(new PublicRegionVerificationProjection(10L))
        );
        when(publicRegionCache.findRegion(10L)).thenReturn(Optional.of(cached));

        List<PublicRegionStaticInfo> result = useCase.get();

        assertThat(result).containsExactly(cached);
        verify(regionService).findPublicRegionVerifications();
        verify(regionService, never()).findPublicRegionStaticInfo(10L);
    }

    @Test
    void get_캐시_미스이면_정적_표시_정보를_조회해_응답을_조립한다() {
        PublicRegionStaticInfo source = new PublicRegionStaticInfo(10L, "GIMHAE", "MySQL 김해시");
        when(regionService.findPublicRegionVerifications()).thenReturn(
            List.of(new PublicRegionVerificationProjection(10L))
        );
        when(publicRegionCache.findRegion(10L)).thenReturn(Optional.empty());
        when(regionService.findPublicRegionStaticInfo(10L)).thenReturn(source);

        List<PublicRegionStaticInfo> result = useCase.get();

        assertThat(result).containsExactly(source);
        verify(regionService).findPublicRegionStaticInfo(10L);
        verify(publicRegionCache).saveRegion(source);
    }
}
