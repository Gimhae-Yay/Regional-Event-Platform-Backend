package io.regionevent.regioneventbackend.domain.region.service;

import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class PublicRegionCacheAside {

    private final PublicRegionCache publicRegionCache;

    public PublicRegionCacheAside(PublicRegionCache publicRegionCache) {
        this.publicRegionCache = Objects.requireNonNull(publicRegionCache, "publicRegionCache must not be null");
    }

    public PublicRegionStaticInfo resolve(PublicRegionStaticInfo source) {
        return publicRegionCache.findRegion(source.regionId())
            .filter(cached -> cached.regionId().equals(source.regionId()))
            .orElseGet(() -> save(source));
    }

    private PublicRegionStaticInfo save(PublicRegionStaticInfo source) {
        publicRegionCache.saveRegion(source);
        return source;
    }
}
