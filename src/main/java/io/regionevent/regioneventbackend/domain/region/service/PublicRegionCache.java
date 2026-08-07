package io.regionevent.regioneventbackend.domain.region.service;

import java.util.Optional;

public interface PublicRegionCache {

    Optional<PublicRegionStaticInfo> findRegion(Long regionId);

    void saveRegion(PublicRegionStaticInfo region);

    void evictRegion(Long regionId);
}
