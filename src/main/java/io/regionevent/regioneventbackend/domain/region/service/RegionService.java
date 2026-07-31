package io.regionevent.regioneventbackend.domain.region.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class RegionService {

    private final RegionRepository regionRepository;

    public RegionService(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    public Region findPublicRegion(Long regionId) {
        return regionRepository.findByRegionIdAndIsPublicTrue(regionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
