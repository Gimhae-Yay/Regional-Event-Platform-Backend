package io.regionevent.regioneventbackend.domain.content.service;

import java.util.Optional;

public interface PublicContentCache {

    Optional<PublicContentStaticInfo> findContent(
        Long regionId,
        Long contentId,
        int versionNo
    );

    void saveContent(PublicContentStaticInfo content);

    void evictContent(Long regionId, Long contentId, int versionNo);
}
