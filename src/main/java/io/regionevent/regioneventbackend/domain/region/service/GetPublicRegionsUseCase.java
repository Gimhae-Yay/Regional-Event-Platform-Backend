package io.regionevent.regioneventbackend.domain.region.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPublicRegionsUseCase {

    private final RegionService regionService;
    private final PublicRegionCacheAside publicRegionCacheAside;

    public GetPublicRegionsUseCase(
        RegionService regionService,
        PublicRegionCacheAside publicRegionCacheAside
    ) {
        this.regionService = regionService;
        this.publicRegionCacheAside = publicRegionCacheAside;
    }

    @Transactional(readOnly = true)
    public List<PublicRegionStaticInfo> get() {
        return regionService.findPublicRegions().stream()
            .map(PublicRegionStaticInfo::from)
            .map(publicRegionCacheAside::resolve)
            .toList();
    }
}
