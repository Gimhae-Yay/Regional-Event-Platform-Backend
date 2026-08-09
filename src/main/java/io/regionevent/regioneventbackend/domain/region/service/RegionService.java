package io.regionevent.regioneventbackend.domain.region.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.region.repository.PublicRegionVerificationProjection;
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

    public Region findRegionForUpdate(Long regionId) {
        return regionRepository.findByRegionId(regionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<PublicRegionVerificationProjection> findPublicRegionVerifications() {
        return regionRepository.findPublicRegionVerifications();
    }

    public PublicRegionStaticInfo findPublicRegionStaticInfo(Long regionId) {
        return regionRepository.findPublicRegionStaticInfo(regionId)
            .map(PublicRegionStaticInfo::from)
            .orElseThrow(() -> new IllegalStateException("public region static info must exist after verification"));
    }
}
