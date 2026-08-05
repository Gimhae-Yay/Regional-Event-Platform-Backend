package io.regionevent.regioneventbackend.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class PublicRegionCacheAsideTest {

    private final PublicRegionCache publicRegionCache = mock(PublicRegionCache.class);
    private final PublicRegionCacheAside cacheAside = new PublicRegionCacheAside(publicRegionCache);

    @Test
    void resolve_같은_지역_식별자의_캐시_값이_있으면_정적_표시_정보_조회_없이_재사용한다() {
        PublicRegionStaticInfo cached = new PublicRegionStaticInfo(10L, "GIMHAE", "캐시 김해시");
        @SuppressWarnings("unchecked")
        Supplier<PublicRegionStaticInfo> staticInfoLoader = mock(Supplier.class);
        when(publicRegionCache.findRegion(10L)).thenReturn(Optional.of(cached));

        PublicRegionStaticInfo result = cacheAside.resolve(10L, staticInfoLoader);

        assertThat(result).isEqualTo(cached);
        verifyNoInteractions(staticInfoLoader);
        verify(publicRegionCache, never()).saveRegion(cached);
    }

    @Test
    void resolve_다른_지역_식별자의_캐시_값은_사용하지_않고_정적_표시_정보를_조회해_저장한다() {
        PublicRegionStaticInfo source = new PublicRegionStaticInfo(10L, "GIMHAE", "김해시");
        @SuppressWarnings("unchecked")
        Supplier<PublicRegionStaticInfo> staticInfoLoader = mock(Supplier.class);
        when(publicRegionCache.findRegion(10L)).thenReturn(
            Optional.of(new PublicRegionStaticInfo(11L, "DONGHAE", "동해시"))
        );
        when(staticInfoLoader.get()).thenReturn(source);

        PublicRegionStaticInfo result = cacheAside.resolve(10L, staticInfoLoader);

        assertThat(result).isEqualTo(source);
        verify(staticInfoLoader).get();
        verify(publicRegionCache).saveRegion(source);
    }
}
