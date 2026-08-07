package io.regionevent.regioneventbackend.domain.region.dto;

import java.util.List;

import io.regionevent.regioneventbackend.domain.region.service.PublicRegionStaticInfo;

public record GetPublicRegionsResponse(List<RegionResponse> regions) {

    public GetPublicRegionsResponse {
        regions = List.copyOf(regions);
    }

    public static GetPublicRegionsResponse from(List<PublicRegionStaticInfo> regions) {
        return new GetPublicRegionsResponse(regions.stream()
            .map(RegionResponse::from)
            .toList());
    }

    public record RegionResponse(
        String regionId,
        String regionCode,
        String name
    ) {

        private static RegionResponse from(PublicRegionStaticInfo region) {
            return new RegionResponse(
                region.regionId().toString(),
                region.regionCode(),
                region.name()
            );
        }
    }
}
