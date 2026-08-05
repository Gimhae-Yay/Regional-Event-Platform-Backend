package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;

class PublicCatalogCacheAsideTest {

    private final PublicCatalogCache publicCatalogCache = mock(PublicCatalogCache.class);
    private final PublicCatalogCacheAside cacheAside = new PublicCatalogCacheAside(publicCatalogCache);

    @Test
    void resolveContent_현재_버전_키의_캐시가_있으면_정적_표시_정보를_재사용한다() {
        PublicContentStaticInfo source = content(3, "MySQL 제목");
        PublicContentStaticInfo cached = content(3, "캐시 제목");
        when(publicCatalogCache.findContent(10L, 200L, 3)).thenReturn(Optional.of(cached));

        PublicContentStaticInfo result = cacheAside.resolveContent(source);

        assertThat(result).isEqualTo(cached);
        verify(publicCatalogCache, never()).saveContent(source);
    }

    @Test
    void resolveContent_이전_버전_캐시가_반환되면_MySQL_원본을_사용하고_현재_키에_저장한다() {
        PublicContentStaticInfo source = content(4, "현재 제목");
        when(publicCatalogCache.findContent(10L, 200L, 4)).thenReturn(Optional.of(content(3, "이전 제목")));

        PublicContentStaticInfo result = cacheAside.resolveContent(source);

        assertThat(result).isEqualTo(source);
        verify(publicCatalogCache).saveContent(source);
    }

    @Test
    void resolveRegion_식별자가_다른_캐시는_사용하지_않고_MySQL_원본을_저장한다() {
        PublicRegionStaticInfo source = new PublicRegionStaticInfo(10L, "GIMHAE", "김해시");
        when(publicCatalogCache.findRegion(10L)).thenReturn(
            Optional.of(new PublicRegionStaticInfo(11L, "DONGHAE", "동해시"))
        );

        PublicRegionStaticInfo result = cacheAside.resolveRegion(source);

        assertThat(result).isEqualTo(source);
        verify(publicCatalogCache).saveRegion(source);
    }

    private static PublicContentStaticInfo content(int versionNo, String title) {
        return new PublicContentStaticInfo(
            10L,
            200L,
            versionNo,
            ContentType.EVENT_EXPERIENCE,
            title,
            "축제 설명",
            "김해시",
            "10:00~18:00",
            "우천 시 취소",
            "전 연령",
            "없음",
            "당일 취소 불가"
        );
    }
}
