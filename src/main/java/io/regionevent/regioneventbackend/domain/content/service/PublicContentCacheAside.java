package io.regionevent.regioneventbackend.domain.content.service;

import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

@Component
public class PublicContentCacheAside {

    private final PublicContentCache publicContentCache;

    public PublicContentCacheAside(PublicContentCache publicContentCache) {
        this.publicContentCache = Objects.requireNonNull(publicContentCache, "publicContentCache must not be null");
    }

    public PublicContentStaticInfo resolveContent(
        Long regionId,
        Long contentId,
        int versionNo,
        Supplier<PublicContentStaticInfo> staticInfoLoader
    ) {
        Objects.requireNonNull(staticInfoLoader, "staticInfoLoader must not be null");
        return publicContentCache.findContent(regionId, contentId, versionNo)
            .filter(cached -> isCurrentContent(cached, regionId, contentId, versionNo))
            .orElseGet(() -> saveContent(staticInfoLoader.get()));
    }

    private PublicContentStaticInfo saveContent(PublicContentStaticInfo source) {
        publicContentCache.saveContent(source);
        return source;
    }

    private boolean isCurrentContent(
        PublicContentStaticInfo cached,
        Long regionId,
        Long contentId,
        int versionNo
    ) {
        return cached.regionId().equals(regionId)
            && cached.contentId().equals(contentId)
            && cached.versionNo() == versionNo;
    }
}
