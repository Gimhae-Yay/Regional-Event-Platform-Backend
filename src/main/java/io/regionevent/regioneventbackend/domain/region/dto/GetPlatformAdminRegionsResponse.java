package io.regionevent.regioneventbackend.domain.region.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.region.service.PlatformAdminRegionListInfo;

public record GetPlatformAdminRegionsResponse(List<RegionResponse> regions) {

    public GetPlatformAdminRegionsResponse {
        regions = List.copyOf(regions);
    }

    public static GetPlatformAdminRegionsResponse from(List<PlatformAdminRegionListInfo> regions) {
        return new GetPlatformAdminRegionsResponse(regions.stream()
            .map(RegionResponse::from)
            .toList());
    }

    public record RegionResponse(
        String regionId,
        String regionCode,
        String name,
        boolean isPublic,
        long regionAdminCount,
        Instant createdAt,
        Instant updatedAt
    ) {

        private static RegionResponse from(PlatformAdminRegionListInfo region) {
            return new RegionResponse(
                region.regionId().toString(),
                region.regionCode(),
                region.name(),
                region.isPublic(),
                region.regionAdminCount(),
                region.createdAt(),
                region.updatedAt()
            );
        }
    }
}
