package io.regionevent.regioneventbackend.domain.content.service;

import java.util.Optional;

public interface PublicCatalogCache {

    Optional<PublicRegionStaticInfo> findRegion(Long regionId);

    void saveRegion(PublicRegionStaticInfo region);

    void evictRegion(Long regionId);

    Optional<PublicContentStaticInfo> findContent(
        Long regionId,
        Long contentId,
        int versionNo
    );

    void saveContent(PublicContentStaticInfo content);

    void evictContent(Long regionId, Long contentId, int versionNo);
}
