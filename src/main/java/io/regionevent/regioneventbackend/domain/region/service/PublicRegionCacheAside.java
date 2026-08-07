package io.regionevent.regioneventbackend.domain.region.service;

import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

@Component
public class PublicRegionCacheAside {

    private final PublicRegionCache publicRegionCache;

    public PublicRegionCacheAside(PublicRegionCache publicRegionCache) {
        this.publicRegionCache = Objects.requireNonNull(publicRegionCache, "publicRegionCache must not be null");
    }

    public PublicRegionStaticInfo resolve(
        Long regionId,
        Supplier<PublicRegionStaticInfo> staticInfoLoader
    ) {
        Objects.requireNonNull(staticInfoLoader, "staticInfoLoader must not be null");
        return publicRegionCache.findRegion(regionId)
            .filter(cached -> cached.regionId().equals(regionId))
            .orElseGet(() -> save(staticInfoLoader.get()));
    }

    private PublicRegionStaticInfo save(PublicRegionStaticInfo source) {
        publicRegionCache.saveRegion(source);
        return source;
    }
}
