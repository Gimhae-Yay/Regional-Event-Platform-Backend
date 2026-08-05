package io.regionevent.regioneventbackend.domain.content.service;

import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class PublicCatalogCacheAside {

    private final PublicCatalogCache publicCatalogCache;

    public PublicCatalogCacheAside(PublicCatalogCache publicCatalogCache) {
        this.publicCatalogCache = Objects.requireNonNull(publicCatalogCache, "publicCatalogCache must not be null");
    }

    public PublicRegionStaticInfo resolveRegion(PublicRegionStaticInfo source) {
        return publicCatalogCache.findRegion(source.regionId())
            .filter(cached -> cached.regionId().equals(source.regionId()))
            .orElseGet(() -> saveRegion(source));
    }

    public PublicContentStaticInfo resolveContent(PublicContentStaticInfo source) {
        return publicCatalogCache.findContent(
            source.regionId(),
            source.contentId(),
            source.versionNo()
        ).filter(cached -> isCurrentContent(cached, source))
            .orElseGet(() -> saveContent(source));
    }

    private PublicRegionStaticInfo saveRegion(PublicRegionStaticInfo source) {
        publicCatalogCache.saveRegion(source);
        return source;
    }

    private PublicContentStaticInfo saveContent(PublicContentStaticInfo source) {
        publicCatalogCache.saveContent(source);
        return source;
    }

    private boolean isCurrentContent(
        PublicContentStaticInfo cached,
        PublicContentStaticInfo source
    ) {
        return cached.regionId().equals(source.regionId())
            && cached.contentId().equals(source.contentId())
            && cached.versionNo() == source.versionNo();
    }
}
