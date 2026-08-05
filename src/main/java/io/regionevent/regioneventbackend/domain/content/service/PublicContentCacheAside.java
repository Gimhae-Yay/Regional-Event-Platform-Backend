package io.regionevent.regioneventbackend.domain.content.service;

import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class PublicContentCacheAside {

    private final PublicContentCache publicContentCache;

    public PublicContentCacheAside(PublicContentCache publicContentCache) {
        this.publicContentCache = Objects.requireNonNull(publicContentCache, "publicContentCache must not be null");
    }

    public PublicContentStaticInfo resolveContent(PublicContentStaticInfo source) {
        return publicContentCache.findContent(
            source.regionId(),
            source.contentId(),
            source.versionNo()
        ).filter(cached -> isCurrentContent(cached, source))
            .orElseGet(() -> saveContent(source));
    }

    private PublicContentStaticInfo saveContent(PublicContentStaticInfo source) {
        publicContentCache.saveContent(source);
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
