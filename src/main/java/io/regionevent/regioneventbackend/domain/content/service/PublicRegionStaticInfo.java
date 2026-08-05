package io.regionevent.regioneventbackend.domain.content.service;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

public record PublicRegionStaticInfo(
    Long regionId,
    String regionCode,
    String name
) {

    public PublicRegionStaticInfo {
        if (regionId == null || regionId <= 0) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (regionCode == null || regionCode.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public static PublicRegionStaticInfo from(Region region) {
        return new PublicRegionStaticInfo(
            region.getRegionId(),
            region.getRegionCode(),
            region.getName()
        );
    }
}
