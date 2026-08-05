package io.regionevent.regioneventbackend.domain.region.dto;

import java.util.List;

import io.regionevent.regioneventbackend.domain.region.entity.Region;

public record GetPublicRegionsResponse(List<RegionResponse> regions) {

    public GetPublicRegionsResponse {
        regions = List.copyOf(regions);
    }

    public static GetPublicRegionsResponse from(List<Region> regions) {
        return new GetPublicRegionsResponse(regions.stream()
            .map(RegionResponse::from)
            .toList());
    }

    public record RegionResponse(
        String regionId,
        String regionCode,
        String name
    ) {

        private static RegionResponse from(Region region) {
            return new RegionResponse(
                region.getRegionId().toString(),
                region.getRegionCode(),
                region.getName()
            );
        }
    }
}
