package io.regionevent.regioneventbackend.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class PublicRegionCacheAsideTest {

    private final PublicRegionCache publicRegionCache = mock(PublicRegionCache.class);
    private final PublicRegionCacheAside cacheAside = new PublicRegionCacheAside(publicRegionCache);

    @Test
    void resolve_같은_지역_식별자의_캐시_값을_재사용한다() {
        PublicRegionStaticInfo source = new PublicRegionStaticInfo(10L, "GIMHAE", "MySQL 김해시");
        PublicRegionStaticInfo cached = new PublicRegionStaticInfo(10L, "GIMHAE", "캐시 김해시");
        when(publicRegionCache.findRegion(10L)).thenReturn(Optional.of(cached));

        PublicRegionStaticInfo result = cacheAside.resolve(source);

        assertThat(result).isEqualTo(cached);
        verify(publicRegionCache, never()).saveRegion(source);
    }

    @Test
    void resolve_다른_지역_식별자의_캐시_값은_사용하지_않고_MySQL_원본을_저장한다() {
        PublicRegionStaticInfo source = new PublicRegionStaticInfo(10L, "GIMHAE", "김해시");
        when(publicRegionCache.findRegion(10L)).thenReturn(
            Optional.of(new PublicRegionStaticInfo(11L, "DONGHAE", "동해시"))
        );

        PublicRegionStaticInfo result = cacheAside.resolve(source);

        assertThat(result).isEqualTo(source);
        verify(publicRegionCache).saveRegion(source);
    }
}
